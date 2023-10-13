package com.google.gson.protobuf;

/**
 * Determines how enum <u>values</u> should be serialized.
 */
public enum EnumSerialization {
    /**
     * Serializes and deserializes enum values using their <b>number</b>. When this is used, custom
     * value names set on enums are ignored.
     */
    NUMBER,
    /**
     * Serializes and deserializes enum values using their <b>name</b>.
     */
    NAME;
}
