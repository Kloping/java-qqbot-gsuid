package io.github.kloping;

import com.alibaba.fastjson.JSON;
import lombok.Data;

/**
 * @author github-kloping
 * @date 2023-06-03
 */
@Data
public class MessageData {
    private String type;
    private Object data;

    @Override
    public String toString() {
        if ("image".equals(type)) return "{\"type\": \"image\",\"data\": \"... length:" + data.toString().length() + "}";
        return JSON.toJSONString(this);
    }
}
