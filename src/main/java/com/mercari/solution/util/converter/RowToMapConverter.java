package com.mercari.solution.util.converter;

import com.mercari.solution.util.schema.RowSchemaUtil;
import org.apache.beam.sdk.schemas.Schema;
import org.apache.beam.sdk.schemas.logicaltypes.EnumerationType;
import org.apache.beam.sdk.values.Row;
import org.joda.time.Instant;
import org.joda.time.ReadableInstant;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.*;
import java.util.stream.Collectors;

public class RowToMapConverter {

    private static final DateTimeFormatter FORMATTER_HH_MM_SS   = DateTimeFormat.forPattern("HH:mm:ss");

    public static Map<String, Object> convert(final Row row) {
        return convertWithFields(row, null);
    }

    public static Map<String, Object> convertWithFields(final Row row, final Collection<String> fields) {
        final Map<String, Object> map = new HashMap<>();
        if(row == null) {
            return map;
        }
        for(final Schema.Field field : row.getSchema().getFields()) {
            if(fields == null || fields.contains(field.getName())) {
                map.put(field.getName(), getValue(field.getType(), row.getValue(field.getName())));
            }
        }
        return map;
    }

    private static Object getValue(final Schema.FieldType type, final Object value) {
        if(value == null) {
            return null;
        }
        switch (type.getTypeName()) {
            case BYTE:
            case INT16:
            case INT32:
            case INT64:
            case BOOLEAN:
            case FLOAT:
            case DOUBLE:
            case STRING:
            case DECIMAL:
                return value;
            case BYTES:
                return Base64.getEncoder().encodeToString(((byte[]) value));
            case DATETIME:
                return java.time.Instant.ofEpochMilli(((ReadableInstant) value).toInstant().getMillis());
            case LOGICAL_TYPE: {
                if(RowSchemaUtil.isLogicalTypeDate(type)) {
                    return value;
                } else if(RowSchemaUtil.isLogicalTypeTime(type)) {
                    return ((Instant) value).toString(FORMATTER_HH_MM_SS);
                } else if(RowSchemaUtil.isLogicalTypeTimestamp(type)) {
                    return java.time.Instant.ofEpochMilli(((ReadableInstant) value).toInstant().getMillis());
                } else if(RowSchemaUtil.isLogicalTypeEnum(type)) {
                    final EnumerationType.Value enumValue = (EnumerationType.Value) value;
                    return RowSchemaUtil.toString(type, enumValue);
                } else {
                    throw new IllegalArgumentException("Unsupported Beam logical type: " + type.getLogicalType().getIdentifier());
                }
            }
            case ROW:
                return convert((Row) value);
            case ITERABLE:
            case ARRAY:
                return ((List<Object>) value).stream()
                        .map(o -> getValue(type.getCollectionElementType(), o))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            case MAP:
            default:
                return null;
        }
    }

}
