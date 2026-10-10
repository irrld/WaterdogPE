/*
 * Copyright 2026 WaterdogTEAM
 * Licensed under the GNU General Public License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.waterdog.waterdogpe.packs;

import dev.waterdog.waterdogpe.packs.types.ZipResourcePack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ZipResourcePackTest {

    @TempDir
    Path dir;

    @Test
    void hashesTheWholeFile() throws Exception {
        Path file = this.pack();

        ZipResourcePack pack = new ZipResourcePack(file);
        try {
            assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)), pack.getHash());
        } finally {
            pack.close();
        }
    }

    @Test
    void hashesWhenOpened() throws Exception {
        Path file = this.pack();
        byte[] opened = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));

        ZipResourcePack pack = new ZipResourcePack(file);
        try {
            // Same size, other bytes: only a hash taken when the pack was opened still matches
            Files.write(file, new byte[(int) Files.size(file)]);
            assertArrayEquals(opened, pack.getHash());
        } finally {
            pack.close();
        }
    }

    @Test
    void readsWholeChunksFromDisk() throws Exception {
        Path file = this.pack();
        byte[] bytes = Files.readAllBytes(file);
        int chunkSize = 100 * 1024;
        int lastOffset = (bytes.length - 1) / chunkSize * chunkSize;

        ZipResourcePack pack = new ZipResourcePack(file);
        try {
            assertArrayEquals(Arrays.copyOfRange(bytes, chunkSize, 2 * chunkSize), pack.getChunk(chunkSize, chunkSize));
            assertArrayEquals(Arrays.copyOfRange(bytes, lastOffset, bytes.length), pack.getChunk(lastOffset, chunkSize));
        } finally {
            pack.close();
        }
    }

    private Path pack() throws Exception {
        Path file = this.dir.resolve("pack.mcpack");
        byte[] content = new byte[3 * 1024 * 1024 + 17];
        new Random(1).nextBytes(content);
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("blob.bin"));
            zip.write(content);
        }
        return file;
    }
}
