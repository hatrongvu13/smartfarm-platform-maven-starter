package com.htv.farmverse.farm.adapter.out.jpa;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Bật Spring Data Auditing (created_at / updated_at). Tách khỏi lớp main để test lát cắt web không cần JPA. */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing
class JpaConfiguration {
}
