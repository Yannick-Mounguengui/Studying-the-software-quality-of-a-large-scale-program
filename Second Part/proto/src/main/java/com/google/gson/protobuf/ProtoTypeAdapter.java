/*
 * Copyright (C) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gson.protobuf;

import static java.util.Objects.requireNonNull;

import com.google.common.base.CaseFormat;
import com.google.common.collect.MapMaker;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.protobuf.DescriptorProtos.EnumValueOptions;
import com.google.protobuf.DescriptorProtos.FieldOptions;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Extension;
import com.google.protobuf.Message;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * GSON type adapter for protocol buffers that knows how to serialize enums either by using their
 * values or their names, and also supports custom proto field names.
 * <p>
 * You can specify which case representation is used for the proto fields when writing/reading the
 * JSON payload by calling {@link Builder#setFieldNameSerializationFormat(CaseFormat, CaseFormat)}.
 * <p>
 * An example of default serialization/deserialization using custom proto field names is shown
 * below:
 *
 * <pre>
 * message MyMessage {
 *   // Will be serialized as 'osBuildID' instead of the default 'osBuildId'.
 *   string os_build_id = 1 [(serialized_name) = "osBuildID"];
 * }
 * </pre>
 *
 * @author Inderjeet Singh
 * @author Emmanuel Cron
 * @author Stanley Wang
 */
public class ProtoTypeAdapter
    implements JsonSerializer<Message>, JsonDeserializer<Message> {

  private final EnumValue enumValue = new EnumValue(this);

  /**
   * Creates a new {@link ProtoTypeAdapter} builder, defaulting enum serialization to
   * {@link EnumSerialization#NAME} and converting field serialization from
   * {@link CaseFormat#LOWER_UNDERSCORE} to {@link CaseFormat#LOWER_CAMEL}.
   */
  public static Builder newBuilder() {
    return new Builder(EnumSerialization.NAME, CaseFormat.LOWER_UNDERSCORE, CaseFormat.LOWER_CAMEL);
  }

  private static final com.google.protobuf.Descriptors.FieldDescriptor.Type ENUM_TYPE =
      com.google.protobuf.Descriptors.FieldDescriptor.Type.ENUM;

  private static final ConcurrentMap<String, ConcurrentMap<Class<?>, Method>> mapOfMapOfMethods =
      new MapMaker().makeMap();

  private final EnumSerialization enumSerialization;
  private final CaseFormat protoFormat;
  private final CaseFormat jsonFormat;
  private final Set<Extension<FieldOptions, String>> serializedNameExtensions;
  private final Set<Extension<EnumValueOptions, String>> serializedEnumValueExtensions;

  protected ProtoTypeAdapter(EnumSerialization enumSerialization,
                             CaseFormat protoFormat,
                             CaseFormat jsonFormat,
                             Set<Extension<FieldOptions, String>> serializedNameExtensions,
                             Set<Extension<EnumValueOptions, String>> serializedEnumValueExtensions) {
    this.enumSerialization = enumSerialization;
    this.protoFormat = protoFormat;
    this.jsonFormat = jsonFormat;
    this.serializedNameExtensions = serializedNameExtensions;
    this.serializedEnumValueExtensions = serializedEnumValueExtensions;
  }

  @Override
  public JsonElement serialize(Message src, Type typeOfSrc,
      JsonSerializationContext context) {
    JsonObject ret = new JsonObject();
    final Map<FieldDescriptor, Object> fields = src.getAllFields();

    for (Map.Entry<FieldDescriptor, Object> fieldPair : fields.entrySet()) {
      final FieldDescriptor desc = fieldPair.getKey();
      String name = getCustSerializedName(desc.getOptions(), desc.getName());

      if (desc.getType() == ENUM_TYPE) {
        // Enum collections are also returned as ENUM_TYPE
        if (fieldPair.getValue() instanceof Collection) {
          // Build the array to avoid infinite loop
          JsonArray array = new JsonArray();
          @SuppressWarnings("unchecked")
          Collection<EnumValueDescriptor> enumDescs =
              (Collection<EnumValueDescriptor>) fieldPair.getValue();
          for (EnumValueDescriptor enumDesc : enumDescs) {
            array.addElement(context.serialize(enumValue.getEnumValue(enumDesc)));
            ret.add(name, array);
          }
        } else {
          EnumValueDescriptor enumDesc = ((EnumValueDescriptor) fieldPair.getValue());
          ret.add(name, context.serialize(enumValue.getEnumValue(enumDesc)));
        }
      } else {
        ret.add(name, context.serialize(fieldPair.getValue()));
      }
    }
    return ret;
  }

  @Override
  public Message deserialize(JsonElement json, Type typeOfT,
      JsonDeserializationContext context) throws JsonParseException {
    try {
      JsonObject jsonObject = json.getAsJsonObject();
      @SuppressWarnings("unchecked")
      Class<? extends Message> protoClass = (Class<? extends Message>) typeOfT;

      if (DynamicMessage.class.isAssignableFrom(protoClass)) {
        throw new IllegalStateException("only generated messages are supported");
      }

      try {
        return BuildProto(context, jsonObject, protoClass);
      } catch (SecurityException | InvocationTargetException | IllegalAccessException | IllegalArgumentException |
               NoSuchMethodException e) {
        throw new JsonParseException(e);
      }
    } catch (Exception e) {
      throw new JsonParseException("Error while parsing proto", e);
    }
  }

  private Message BuildProto(JsonDeserializationContext context, JsonObject jsonObject, Class<? extends Message> protoClass) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException, NoSuchFieldException {
    // Invoke the ProtoClass.newBuilder() method
    Message.Builder protoBuilder =
        (Message.Builder) getCachedMethod(protoClass, "newBuilder").invoke(null);

    Message defaultInstance =
        (Message) getCachedMethod(protoClass, "getDefaultInstance").invoke(null);

    Descriptor protoDescriptor =
        (Descriptor) getCachedMethod(protoClass, "getDescriptor").invoke(null);
    // Call setters on all of the available fields
    for (FieldDescriptor fieldDescriptor : protoDescriptor.getFields()) {
      String jsonFieldName =
          getCustSerializedName(fieldDescriptor.getOptions(), fieldDescriptor.getName());

      JsonElement jsonElement = jsonObject.get(jsonFieldName);
      if (jsonElement != null && !jsonElement.isJsonNull()) {
        // Do not reuse jsonFieldName here, it might have a custom value
        Object fieldValue;
        FieldDescriptorType(context, protoClass, protoBuilder, defaultInstance, fieldDescriptor, jsonElement);
      }
    }
    return protoBuilder.build();
  }

  private void FieldDescriptorType(JsonDeserializationContext context, Class<? extends Message> protoClass, Message.Builder protoBuilder, Message defaultInstance, FieldDescriptor fieldDescriptor, JsonElement jsonElement) throws NoSuchFieldException {
    Object fieldValue;
    if (fieldDescriptor.getType() == ENUM_TYPE) {
      if (jsonElement.isJsonArray()) {
        // Handling array
        Collection<EnumValueDescriptor> enumCollection =
            new ArrayList<>(jsonElement.getAsJsonArray().size());
        for (JsonElement element : jsonElement.getAsJsonArray()) {
          enumCollection.add(
                  enumValue.findValueByNameAndExtension(fieldDescriptor.getEnumType(), element));
        }
        fieldValue = enumCollection;
      } else {
        // No array, just a plain value
        fieldValue =
                enumValue.findValueByNameAndExtension(fieldDescriptor.getEnumType(), jsonElement);
      }
      protoBuilder.setField(fieldDescriptor, fieldValue);
    } else if (fieldDescriptor.isRepeated()) {
      // If the type is an array, then we have to grab the type from the class.
      // protobuf java field names are always lower camel case
      FieldRepeated(context, protoClass, protoBuilder, fieldDescriptor, jsonElement);
    } else {
      Object field = defaultInstance.getField(fieldDescriptor);
      fieldValue = context.deserialize(jsonElement, field.getClass());
      protoBuilder.setField(fieldDescriptor, fieldValue);
    }
  }

  private void FieldRepeated(JsonDeserializationContext context, Class<? extends Message> protoClass, Message.Builder protoBuilder, FieldDescriptor fieldDescriptor, JsonElement jsonElement) throws NoSuchFieldException {
    Object fieldValue;
    String protoArrayFieldName =
        protoFormat.to(CaseFormat.LOWER_CAMEL, fieldDescriptor.getName()) + "_";
    Field protoArrayField = protoClass.getDeclaredField(protoArrayFieldName);
    Type protoArrayFieldType = protoArrayField.getGenericType();
    fieldValue = context.deserialize(jsonElement, protoArrayFieldType);
    protoBuilder.setField(fieldDescriptor, fieldValue);
  }

  /**
   * Retrieves the custom field name from the given options, and if not found, returns the specified
   * default name.
   */
  private String getCustSerializedName(FieldOptions options, String defaultName) {
    for (Extension<FieldOptions, String> extension : serializedNameExtensions) {
      if (options.hasExtension(extension)) {
        return options.getExtension(extension);
      }
    }
    return protoFormat.to(jsonFormat, defaultName);
  }

  /**
   * Returns the enum value to use for serialization, depending on the value of
   * {@link EnumSerialization} that was given to this adapter.
   */
  private Object getEnumValue(EnumValueDescriptor enumDesc) {
    return enumValue.getEnumValue(enumDesc);
  }

  /**
   * Finds an enum value in the given {@link EnumDescriptor} that matches the given JSON element,
   * either by name if the current adapter is using {@link EnumSerialization#NAME}, otherwise by
   * number. If matching by name, it uses the extension value if it is defined, otherwise it uses
   * its default value.
   *
   * @throws IllegalArgumentException if a matching name/number was not found
   */
  private EnumValueDescriptor findValueByNameAndExtension(EnumDescriptor desc,
      JsonElement jsonElement) {
    return enumValue.findValueByNameAndExtension(desc, jsonElement);
  }

  private static Method getCachedMethod(Class<?> clazz, String methodName,
      Class<?>... methodParamTypes) throws NoSuchMethodException {
    ConcurrentMap<Class<?>, Method> mapOfMethods = mapOfMapOfMethods.get(methodName);
    if (mapOfMethods == null) {
      mapOfMethods = new MapMaker().makeMap();
      ConcurrentMap<Class<?>, Method> previous =
          mapOfMapOfMethods.putIfAbsent(methodName, mapOfMethods);
      mapOfMethods = previous == null ? mapOfMethods : previous;
    }

    Method method = mapOfMethods.get(clazz);
    if (method == null) {
      method = clazz.getMethod(methodName, methodParamTypes);
      mapOfMethods.putIfAbsent(clazz, method);
      // NB: it doesn't matter which method we return in the event of a race.
    }
    return method;
  }

  public EnumSerialization getEnumSerialization() {
    return enumSerialization;
  }

  public Set<Extension<EnumValueOptions, String>> getSerializedEnumValueExtensions() {
    return serializedEnumValueExtensions;
  }
}
