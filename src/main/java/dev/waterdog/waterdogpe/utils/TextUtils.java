/*
 * Copyright 2025 WaterdogTEAM
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

package dev.waterdog.waterdogpe.utils;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.ComponentSerializer;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import net.kyori.adventure.text.serializer.json.JSONOptions;
import org.cloudburstmc.protocol.bedrock.codec.BedrockLegacyTextSerializer;

public class TextUtils {

    @Getter
    private static final ComponentSerializer<Component, Component, String> componentSerializer = JSONComponentSerializer.builder()
            .editOptions(builder -> builder.value(JSONOptions.EMIT_RGB, false))
            .build();

    @Getter
    private static final ComponentSerializer<Component, Component, String> legacyComponentSerializer = BedrockLegacyTextSerializer.getInstance();

    public static String componentToString(Component component) {
        return legacyComponentSerializer.serialize(component);
    }

    public static Component stringToComponent(String string) {
        return legacyComponentSerializer.deserialize(string);
    }

}
