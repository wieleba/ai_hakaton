package com.hackathon.features.jabber;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(JabberProperties.class)
public class JabberConfig {}
