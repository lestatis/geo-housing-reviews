/**
 * Domain layer of the moderation module: reports, cases, decisions and appeals.
 *
 * <p>Framework-free by rule — no Spring, no JPA, no HTTP. Content under moderation belongs to other
 * modules and is referenced only by opaque {@link
 * com.example.geohousing.moderation.domain.ModerationTargetRef}; a decision's effect is applied
 * through the owning module's published contract, never by reaching into its tables.
 */
package com.example.geohousing.moderation.domain;
