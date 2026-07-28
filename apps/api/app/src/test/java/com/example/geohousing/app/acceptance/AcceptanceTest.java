package com.example.geohousing.app.acceptance;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * Runs every {@code .feature} file against the real HTTP stack.
 *
 * <p>These scenarios are the product's behaviour written so that someone who does not read Java can
 * check it — the privacy, moderation and trust rules here are commitments, not implementation
 * details. Fine-grained negative paths stay in the JUnit endpoint tests, which are a different
 * altitude rather than a competing one (ADR-0009).
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = "cucumber.glue", value = "com.example.geohousing.app.acceptance")
@ConfigurationParameter(key = "cucumber.plugin", value = "pretty, summary")
// Cucumber 7 is strict by default: a step with no matching definition fails the scenario rather
// than being skipped, which is what keeps these files from becoming documentation that proves
// nothing.
public class AcceptanceTest {}
