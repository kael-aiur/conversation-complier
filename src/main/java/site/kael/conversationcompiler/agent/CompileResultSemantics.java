package site.kael.conversationcompiler.agent;

/** Shared reporting rules: describe routine handling in the summary, not as warnings. */
final class CompileResultSemantics {
    private CompileResultSemantics() { }

    static final String SUMMARY_DESCRIPTION = "本次整理的成果摘要：说明新增、更新、核对后跳过的知识；必要的正常处理说明也放在这里。";
    static final String WARNINGS_DESCRIPTION = "仅报告本次整理结束时仍未解决、且实际影响结果质量、完整性或写入可信度的问题；"
            + "每条说明具体问题及影响，没有此类问题必须返回空数组 []。正常去重、零新增、隐私过滤、"
            + "可选 contentHash 缺失、已纠正成功的工具错误，以及原会话中的开发未完成状态不是整理警告。";
    static final String CONTENT_HASH_DESCRIPTION = "可选的已写入内容哈希；只有取得可靠值时填写，否则留空或 null。不得编造，也不得仅因该字段缺失生成警告。";

    static final String RULES = """
            compile_result 的总结与警告语义：
            - summary 描述本次整理成果、正常取舍和必要背景；普通完成总结和处理说明不得放入 warnings。
            - warnings 只报告本次整理结束时仍未解决、且实际影响结果质量、完整性或写入可信度的问题。
            - 真正的警告包括：应保存的知识最终未能写入；写入结果仍不确定；关键证据缺失影响本次应整理的结论；知识冲突未解决影响安全合并或一致性。每条必须说明具体问题及实际影响。
            - 以下不是警告：可选 contentHash 未获取；已有知识核对后跳过或零新增；正常过滤敏感信息、临时问答和重复内容；未确认建议未作为正式决策保存；原会话中的功能尚未开发或部署且已如实标注；工具曾失败但已纠正并确认成功；历史记录缺失但不影响本次提取的结论。
            - 必要的普通处理说明放入 summary，无需逐项报告正常操作。原会话中的失败或待办是知识内容，不等于本次整理失败或警告。
            - contentHash 有可靠值就填写，没有就留空或 null，不得伪造，不因此生成警告。
            - 没有上述真正警告时，warnings 必须返回 []；不得用泛泛的“记录可能不完整”或“遵守隐私规则”填充。
            - 不得为了得到无警告完成而隐藏实际未解决的错误、未保存内容或不确定写入。
            """;
}
