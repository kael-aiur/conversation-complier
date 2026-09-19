package site.kael.conversationcompiler.domain;

public record IngestResult(int accepted, int duplicates, int errors) {}
