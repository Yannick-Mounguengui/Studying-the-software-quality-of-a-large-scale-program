package com.google.gson.protobuf;

import com.google.gson.JsonElement;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Extension;

public class EnumValue {
    private final ProtoTypeAdapter protoTypeAdapter;

    public EnumValue(ProtoTypeAdapter protoTypeAdapter) {
        this.protoTypeAdapter = protoTypeAdapter;
    }

    /**
     * Retrieves the custom enum value name from the given options, and if not found, returns the
     * specified default value.
     */
    String getCustSerializedEnumValue(DescriptorProtos.EnumValueOptions options, String defaultValue) {
        for (Extension<DescriptorProtos.EnumValueOptions, String> extension : protoTypeAdapter.getSerializedEnumValueExtensions()) {
            if (options.hasExtension(extension)) {
                return options.getExtension(extension);
            }
        }
        return defaultValue;
    }

    /**
     * Returns the enum value to use for serialization, depending on the value of
     * {@link EnumSerialization} that was given to this adapter.
     */
    Object getEnumValue(Descriptors.EnumValueDescriptor enumDesc) {
        if (protoTypeAdapter.getEnumSerialization() == EnumSerialization.NAME) {
            return getCustSerializedEnumValue(enumDesc.getOptions(), enumDesc.getName());
        } else {
            return enumDesc.getNumber();
        }
    }

    /**
     * Finds an enum value in the given {@link Descriptors.EnumDescriptor} that matches the given JSON element,
     * either by name if the current adapter is using {@link EnumSerialization#NAME}, otherwise by
     * number. If matching by name, it uses the extension value if it is defined, otherwise it uses
     * its default value.
     *
     * @throws IllegalArgumentException if a matching name/number was not found
     */
    Descriptors.EnumValueDescriptor findValueByNameAndExtension(Descriptors.EnumDescriptor desc,
                                                                JsonElement jsonElement) {
        if (protoTypeAdapter.getEnumSerialization() == EnumSerialization.NAME) {
            // With enum name
            for (Descriptors.EnumValueDescriptor enumDesc : desc.getValues()) {
                String enumValue = getCustSerializedEnumValue(enumDesc.getOptions(), enumDesc.getName());
                if (enumValue.equals(jsonElement.getAsString())) {
                    return enumDesc;
                }
            }
            throw new IllegalArgumentException(
                    String.format("Unrecognized enum name: %s", jsonElement.getAsString()));
        } else {
            // With enum value
            Descriptors.EnumValueDescriptor fieldValue = desc.findValueByNumber(jsonElement.getAsInt());
            if (fieldValue == null) {
                throw new IllegalArgumentException(
                        String.format("Unrecognized enum value: %d", jsonElement.getAsInt()));
            }
            return fieldValue;
        }
    }
}