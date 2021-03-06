/*******************************************************************************
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.hono.service.metric;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.graphite.GraphiteConfig;
import io.micrometer.graphite.GraphiteHierarchicalNameMapper;
import io.micrometer.graphite.GraphiteMeterRegistry;
import io.micrometer.spring.autoconfigure.MeterRegistryCustomizer;
import io.vertx.core.metrics.MetricsOptions;

/**
 * Configuration for using legacy style metrics.
 */
@PropertySource("classpath:org/eclipse/hono/service/metric/legacy.properties")
public abstract class AbstractLegacyMetricsConfig {

    protected static final String TAG_HONO = "hono";
    protected static final String TAG_METER_TYPE = "meterType";
    protected static final String TAG_SUB_NAME = "subName";
    protected static final String TAG_TYPE_SUFFIX = "typeSuffix";

    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Provide Vert.x metrics options for the legacy metrics setup.
     * 
     * @return Metrics options for the legacy setup.
     */
    @Bean
    public MetricsOptions metricsOptions() {
        return new MetricsOptions();
    }

    /**
     * Create a new graphite meter registry, using the legacy format.
     * 
     * @param config The config to use.
     * @param clock The clock to use.
     * @return The new meter registry.
     */
    @Bean
    public GraphiteMeterRegistry graphiteMeterRegistry(final GraphiteConfig config, final Clock clock) {
        return new GraphiteMeterRegistry(config, clock,
                legacyGraphiteFormatMapper(new GraphiteHierarchicalNameMapper(config.tagsAsPrefix())));
    }

    /**
     * Gets the object to use for mapping a Micrometer meter name and tags
     * to a hierarchical Graphite-compliant meter name.
     * 
     * @param defaultMapper The default mapper to use for non-Hono specific
     *                      meters.
     * @return The mapper.
     */
    protected HierarchicalNameMapper legacyGraphiteFormatMapper(
            final GraphiteHierarchicalNameMapper defaultMapper) {

        return (id, convention) -> {

            if (id.getTag(TAG_HONO) == null) {
                return defaultMapper.toHierarchicalName(id, convention);
            }

            return amendWithTags(id.getConventionName(convention), id,
                    new String[] { MetricsTags.TAG_HOST, TAG_METER_TYPE, TAG_HONO, MetricsTags.TAG_PROTOCOL },
                    new String[] { MetricsTags.TAG_TYPE, MetricsTags.TAG_TENANT, TAG_SUB_NAME, TAG_TYPE_SUFFIX });
        };
    }

    /**
     * Meter registry customizer for the legacy metrics model.
     * 
     * @return The customizer for the legacy model.
     */
    @Bean
    public MeterRegistryCustomizer<GraphiteMeterRegistry> legacyMeterFilters() {
        return r -> r.config()
                .namingConvention(NamingConvention.dot)
                .meterFilter(MeterFilter.replaceTagValues(MetricsTags.TAG_HOST, host -> host.replace('.', '_')))
                .meterFilter(MeterFilter.replaceTagValues(MetricsTags.TAG_TENANT, tenant -> tenant.replace('.', '_')))
                .meterFilter(MeterFilter.ignoreTags(MetricsTags.TAG_COMPONENT))
                .meterFilter(meterTypeMapper());
    }

    /**
     * Gets a filter for mapping an original meter name with tags (as reported by Hono)
     * to a meter name and tags that can be used to derive the corresponding legacy Graphite meter name.
     * 
     * @return The filter.
     */
    protected abstract MeterFilter meterTypeMapper();

    private String amendWithTags(
            final String name,
            final Meter.Id id,
            final String[] prefixTags,
            final String[] suffixTags) {

        final StringBuilder sb = new StringBuilder();

        addTags(sb, id, prefixTags);

        if (sb.length() > 0) {
            sb.append('.');
            sb.append(name);
        }

        addTags(sb, id, suffixTags);

        final String result = sb.toString();
        log.trace("mapping meter [{}] and tags to [{}]", name, result);
        return result;
    }

    private static void addTags(final StringBuilder sb, final Meter.Id id, final String[] tags) {
        for (final String tag : tags) {
            final String value = id.getTag(tag);
            if (value != null) {
                if (sb.length() > 0) {
                    sb.append('.');
                }
                sb.append(value.replace(' ', '_'));
            }
        }
    }

}
