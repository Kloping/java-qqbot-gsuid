package io.github.kloping;

import lombok.Data;

import java.util.Arrays;

/**
 * @author github.kloping
 */
@Data
public class MessageOut {
    private String bot_id;
    private String bot_self_id;
    private String msg_id;
    private String target_type;
    private String target_id;
    private MessageData[] content;

    @Override
    public String toString() {
        return String.format("{\"bot_id\": \"%s\"\"bot_self_id\": \"%s\"\"content\": %s\"msg_id\": \"%s\"\"target_id\": \"%s\"\"target_type\": \"%s\"}"
                , bot_id, bot_self_id, Arrays.toString(content), msg_id, target_id, target_type);
    }
}
