package dev.hua.ptcglive.rules;

import java.time.LocalDate;

/** Traceable source metadata stored with each ruleset, never an implicit web scrape. */
public record RuleSource(
    String titleZh, String url, LocalDate publishedDate, LocalDate effectiveDate,
    String region, String documentVersion, String noteZh
) { }
