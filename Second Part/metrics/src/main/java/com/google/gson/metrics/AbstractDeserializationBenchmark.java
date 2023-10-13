package com.google.gson.metrics;


import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.caliper.BeforeExperiment;
import com.google.caliper.Benchmark;

/**
 * Abstract benchmark class for Gson deserialization benchmarks.
 */
public abstract class AbstractDeserializationBenchmark {

    protected Gson gson;
    protected String json;

    @BeforeExperiment
    void setUp() throws Exception {
        this.gson = new Gson();
        BagOfPrimitives bag = new BagOfPrimitives(10L, 1, false, "foo");
        this.json = gson.toJson(bag);
    }

    protected BagOfPrimitives deserializeBagOfPrimitives(JsonReader jr) throws IOException, IllegalAccessException {
        jr.beginObject();
        BagOfPrimitives bag = new BagOfPrimitives();
        while (jr.hasNext()) {
            String name = jr.nextName();
            for (Field field : BagOfPrimitives.class.getDeclaredFields()) {
                if (field.getName().equals(name)) {
                    Class<?> fieldType = field.getType();
                    if (fieldType.equals(long.class)) {
                        field.setLong(bag, jr.nextLong());
                    } else if (fieldType.equals(int.class)) {
                        field.setInt(bag, jr.nextInt());
                    } else if (fieldType.equals(boolean.class)) {
                        field.setBoolean(bag, jr.nextBoolean());
                    } else if (fieldType.equals(String.class)) {
                        field.set(bag, jr.nextString());
                    } else {
                        throw new RuntimeException("Unexpected: type: " + fieldType + ", name: " + name);
                    }
                }
            }
        }
        jr.endObject();
        return bag;
    }

    /**
     * Benchmark to measure deserializing objects using streaming.
     */
    @Benchmark
    public void timeBagOfPrimitivesStreaming(int reps) throws IOException, IllegalAccessException {
        for (int i = 0; i < reps; ++i) {
            StringReader reader = new StringReader(json);
            JsonReader jr = new JsonReader(reader);
            deserializeBagOfPrimitives(jr);
        }
    }

    /**
     * Benchmark to measure deserializing objects using reflection.
     */
    @Benchmark
    public void timeBagOfPrimitivesReflectionStreaming(int reps) throws Exception {
        for (int i = 0; i < reps; ++i) {
            StringReader reader = new StringReader(json);
            JsonReader jr = new JsonReader(reader);
            BagOfPrimitives bag = deserializeBagOfPrimitives(jr);
        }
    }

}

