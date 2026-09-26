/*
 * Copyright (C) 2026 RBMK Simulator contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.hartrusion.rbmksim.jev;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader and string escaper for the Jev request and response.
 * The simulator has no JSON library on the classpath. This parser accepts
 * the subset the System One API uses: objects, arrays, strings, numbers,
 * booleans, and null.
 */
final class JevJson {

    private final String text;
    private int index;

    private JevJson(String text) {
        this.text = text;
    }

    static Map<String, Object> parseObject(String text) throws JevUnavailableException {
        Object value = new JevJson(text).parseValue();
        if (!(value instanceof Map<?, ?> map)) {
            throw new JevUnavailableException("Jev response was not a JSON object");
        }
        Map<String, Object> typed = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new JevUnavailableException("Jev response object key was not a string");
            }
            typed.put(key, entry.getValue());
        }
        return typed;
    }

    static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
        return out.toString();
    }

    private Object parseValue() throws JevUnavailableException {
        skipSpace();
        if (index >= text.length()) {
            throw new JevUnavailableException("Jev response ended early");
        }
        char c = text.charAt(index);
        if (c == '{') {
            return parseObject();
        }
        if (c == '[') {
            return parseArray();
        }
        if (c == '"') {
            return parseString();
        }
        if (c == 't' || c == 'f') {
            return parseBoolean();
        }
        if (c == 'n') {
            return parseNull();
        }
        if (c == '-' || (c >= '0' && c <= '9')) {
            return parseNumber();
        }
        throw new JevUnavailableException("Unexpected character in Jev response at " + index);
    }

    private Map<String, Object> parseObject() throws JevUnavailableException {
        expect('{');
        Map<String, Object> object = new LinkedHashMap<>();
        skipSpace();
        if (peek('}')) {
            index++;
            return object;
        }
        while (true) {
            skipSpace();
            String key = parseString();
            skipSpace();
            expect(':');
            object.put(key, parseValue());
            skipSpace();
            if (peek('}')) {
                index++;
                return object;
            }
            expect(',');
        }
    }

    private List<Object> parseArray() throws JevUnavailableException {
        expect('[');
        List<Object> array = new ArrayList<>();
        skipSpace();
        if (peek(']')) {
            index++;
            return array;
        }
        while (true) {
            array.add(parseValue());
            skipSpace();
            if (peek(']')) {
                index++;
                return array;
            }
            expect(',');
        }
    }

    private String parseString() throws JevUnavailableException {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (index < text.length()) {
            char c = text.charAt(index++);
            if (c == '"') {
                return out.toString();
            }
            if (c == '\\') {
                if (index >= text.length()) {
                    throw new JevUnavailableException("Unfinished escape in Jev response");
                }
                char escaped = text.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> out.append(escaped);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (index + 4 > text.length()) {
                            throw new JevUnavailableException("Short unicode escape in Jev response");
                        }
                        int code = Integer.parseInt(text.substring(index, index + 4), 16);
                        out.append((char) code);
                        index += 4;
                    }
                    default -> throw new JevUnavailableException("Bad escape in Jev response");
                }
            } else {
                out.append(c);
            }
        }
        throw new JevUnavailableException("Unclosed string in Jev response");
    }

    private Boolean parseBoolean() throws JevUnavailableException {
        if (text.startsWith("true", index)) {
            index += 4;
            return Boolean.TRUE;
        }
        if (text.startsWith("false", index)) {
            index += 5;
            return Boolean.FALSE;
        }
        throw new JevUnavailableException("Bad boolean in Jev response");
    }

    private Object parseNull() throws JevUnavailableException {
        if (text.startsWith("null", index)) {
            index += 4;
            return null;
        }
        throw new JevUnavailableException("Bad null in Jev response");
    }

    private Double parseNumber() throws JevUnavailableException {
        int start = index;
        if (peek('-')) {
            index++;
        }
        while (index < text.length() && text.charAt(index) >= '0' && text.charAt(index) <= '9') {
            index++;
        }
        if (peek('.')) {
            index++;
            while (index < text.length() && text.charAt(index) >= '0' && text.charAt(index) <= '9') {
                index++;
            }
        }
        if (peek('e') || peek('E')) {
            index++;
            if (peek('+') || peek('-')) {
                index++;
            }
            while (index < text.length() && text.charAt(index) >= '0' && text.charAt(index) <= '9') {
                index++;
            }
        }
        try {
            return Double.valueOf(text.substring(start, index));
        } catch (NumberFormatException ex) {
            throw new JevUnavailableException("Bad number in Jev response");
        }
    }

    private void skipSpace() {
        while (index < text.length()) {
            char c = text.charAt(index);
            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                return;
            }
            index++;
        }
    }

    private boolean peek(char expected) {
        return index < text.length() && text.charAt(index) == expected;
    }

    private void expect(char expected) throws JevUnavailableException {
        if (!peek(expected)) {
            throw new JevUnavailableException(
                    "Expected '" + expected + "' in Jev response at " + index);
        }
        index++;
    }
}
