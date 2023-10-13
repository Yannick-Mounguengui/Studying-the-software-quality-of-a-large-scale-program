package com.google.gson.protobuf;

import com.google.common.base.CaseFormat;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Extension;

import java.util.HashSet;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Builder for {@link ProtoTypeAdapter}s.
 */
public class Builder {
    private final Set<Extension<DescriptorProtos.FieldOptions, String>> serializedNameExtensions;
    private final Set<Extension<DescriptorProtos.EnumValueOptions, String>> serializedEnumValueExtensions;
    private EnumSerialization enumSerialization;
    private CaseFormat protoFormat;
    private CaseFormat jsonFormat;

    Builder(EnumSerialization enumSerialization, CaseFormat fromFieldNameFormat,
            CaseFormat toFieldNameFormat) {
        this.serializedNameExtensions = new HashSet<>();
        this.serializedEnumValueExtensions = new HashSet<>();
        setEnumSerialization(enumSerialization);
        setFieldNameSerializationFormat(fromFieldNameFormat, toFieldNameFormat);
    }

    public Builder setEnumSerialization(EnumSerialization enumSerialization) {
        this.enumSerialization = requireNonNull(enumSerialization);
        return this;
    }

    /**
     * Sets the field names serialization format. The first parameter defines how to read the format
     * of the proto field names you are converting to JSON. The second parameter defines which
     * format to use when serializing them.
     * <p>
     * For example, if you use the following parameters: {@link CaseFormat#LOWER_UNDERSCORE},
     * {@link CaseFormat#LOWER_CAMEL}, the following conversion will occur:
     *
     * <pre>{@code
     * PROTO     <->  JSON
     * my_field       myField
     * foo            foo
     * n__id_ct       nIdCt
     * }</pre>
     */
    public Builder getFieldNameSerializationFormat() {
        return this;
    }

    public Builder setFieldNameSerializationFormat(CaseFormat fromFieldNameFormat, CaseFormat toFieldNameFormat) {
        this.protoFormat = fromFieldNameFormat;
        this.jsonFormat = toFieldNameFormat;
        return this;
    }


    /**
     * Adds a field proto annotation that, when set, overrides the default field name
     * serialization/deserialization. For example, if you add the '{@code serialized_name}'
     * annotation and you define a field in your proto like the one below:
     *
     * <pre>
     * string client_app_id = 1 [(serialized_name) = "appId"];
     * </pre>
     * <p>
     * ...the adapter will serialize the field using '{@code appId}' instead of the default '
     * {@code clientAppId}'. This lets you customize the name serialization of any proto field.
     */
    public Builder addSerializedNameExtension(
            Extension<DescriptorProtos.FieldOptions, String> serializedNameExtension) {
        serializedNameExtensions.add(requireNonNull(serializedNameExtension));
        return this;
    }

    /**
     * Adds an enum value proto annotation that, when set, overrides the default <b>enum</b> value
     * serialization/deserialization of this adapter. For example, if you add the '
     * {@code serialized_value}' annotation and you define an enum in your proto like the one below:
     *
     * <pre>
     * enum MyEnum {
     *   UNKNOWN = 0;
     *   CLIENT_APP_ID = 1 [(serialized_value) = "APP_ID"];
     *   TWO = 2 [(serialized_value) = "2"];
     * }
     * </pre>
     * <p>
     * ...the adapter will serialize the value {@code CLIENT_APP_ID} as "{@code APP_ID}" and the
     * value {@code TWO} as "{@code 2}". This works for both serialization and deserialization.
     * <p>
     * Note that you need to set the enum serialization of this adapter to
     * {@link EnumSerialization#NAME}, otherwise these annotations will be ignored.
     */
    public Builder addSerializedEnumValueExtension(
            Extension<DescriptorProtos.EnumValueOptions, String> serializedEnumValueExtension) {
        serializedEnumValueExtensions.add(requireNonNull(serializedEnumValueExtension));
        return this;
    }

    public ProtoTypeAdapter build() {
        return new ProtoTypeAdapter(enumSerialization, protoFormat, jsonFormat,
                serializedNameExtensions, serializedEnumValueExtensions);
    }
}
