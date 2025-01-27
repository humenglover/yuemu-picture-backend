package com.lumenglover.yuemupicturebackend.config;

import org.apache.commons.lang3.time.DateUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchCustomConversions;

import java.text.ParseException;
import java.util.Arrays;
import java.util.Date;

@Configuration
public class ElasticsearchConfig {

    private static final String[] DATE_FORMATS = new String[]{
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
    };

    @Bean
    public ElasticsearchCustomConversions elasticsearchCustomConversions() {
        return new ElasticsearchCustomConversions(
                Arrays.asList(new DateToStringConverter(), new StringToDateConverter())
        );
    }

    @WritingConverter
    static class DateToStringConverter implements Converter<Date, String> {
        @Override
        public String convert(Date source) {
            if (source == null) {
                return null;
            }
            return String.format("%1$tY-%1$tm-%1$tdT%1$tH:%1$tM:%1$tS.%1$tLZ", source);
        }
    }

    @ReadingConverter
    static class StringToDateConverter implements Converter<String, Date> {
        @Override
        public Date convert(String source) {
            if (source == null || source.trim().isEmpty()) {
                return null;
            }
            try {
                return DateUtils.parseDate(source, DATE_FORMATS);
            } catch (ParseException e) {
                throw new IllegalArgumentException("Failed to parse date: " + source, e);
            }
        }
    }
} 