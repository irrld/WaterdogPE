/*
 * Copyright 2022 WaterdogTEAM
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

package dev.waterdog.waterdogpe.packs.types;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipResourcePack extends ResourcePack {

    private final ZipFile zipFile;
    // Taken while loading, not on the network thread when a player first asks for the pack
    private final byte[] hash;
    private ByteBuffer cachedPack;

    public ZipResourcePack(Path file) throws IOException {
        super(file);
        this.hash = sha256(this.packPath);
        try {
            this.zipFile = new ZipFile(this.packPath.toFile());
        } catch (IOException e) {
            throw new IOException("ResourcePack is not zip file!");
        }
    }

    // Streamed, a pack can be larger than the heap
    private static byte[] sha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try (InputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
            input.transferTo(OutputStream.nullOutputStream());
        }
        return digest.digest();
    }

    public ZipEntry getZipEntry(Path path) {
        return this.zipFile.getEntry(path.toString());
    }

    @Override
    public InputStream getStream(Path path) throws IOException {
        ZipEntry entry = this.getZipEntry(path);
        if (entry == null) {
            return null;
        }
        return this.zipFile.getInputStream(entry);
    }

    @Override
    public void close() throws IOException {
        zipFile.close();
    }

    @Override
    public void saveToCache() throws IOException {
        try (FileChannel fileChannel = FileChannel.open(this.packPath)) {
            ByteBuffer buffer = ByteBuffer.allocateDirect((int) fileChannel.size());
            fileChannel.read(buffer);
            buffer.rewind();
            this.cachedPack = buffer;
        }
    }

    @Override
    public ByteBuffer getCachedPack() {
        return this.cachedPack == null ? null : this.cachedPack.slice();
    }

    @Override
    public long getPackSize() {
        try {
            return Files.size(this.packPath);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to get size of pack", e);
        }
    }

    @Override
    public byte[] getHash() {
        return this.hash;
    }

    @Override
    public byte[] getChunk(int offset, int length) {
        byte[] chunkData = new byte[(int) Math.min(this.getPackSize() - offset, length)];

        ByteBuffer cachedPack = this.getCachedPack();
        if (cachedPack != null) {
            cachedPack.position(offset);
            cachedPack.get(chunkData, 0, chunkData.length);
            return chunkData;
        }

        // skip and read may stop short, which would send the rest of the chunk as zeros
        try (InputStream inputStream = Files.newInputStream(this.packPath)) {
            inputStream.skipNBytes(offset);
            inputStream.readNBytes(chunkData, 0, chunkData.length);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read pack chunk", e);
        }
        return chunkData;
    }
}
