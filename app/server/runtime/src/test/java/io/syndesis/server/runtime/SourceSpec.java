package io.syndesis.server.runtime;

public class SourceSpec {
    private String content;
    private String name;

    @Override
    public String toString() {
        return "SourceSpec{" +
            "content='" + content + '\'' +
            ", name='" + name + '\'' +
            '}';
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
