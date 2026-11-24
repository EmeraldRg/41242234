package dev.xetyk.miniassist.modules.api;

import java.util.List;
import java.util.function.BooleanSupplier;

/** Single-choice setting; a click in ClickGUI cycles to the next option. */
public final class ModeSetting {
    private final String name;
    private final String description;
    private final List<String> options;
    private final BooleanSupplier visible;
    private int index;

    public ModeSetting(String name, String description, List<String> options, int defaultIndex) {
        this(name, description, options, defaultIndex, () -> true);
    }

    public ModeSetting(String name, String description, List<String> options, int defaultIndex,
                       BooleanSupplier visible) {
        if (options == null || options.isEmpty()) throw new IllegalArgumentException("No options");
        this.name = name;
        this.description = description;
        this.options = List.copyOf(options);
        this.visible = visible;
        setIndex(defaultIndex);
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isVisible() { return visible.getAsBoolean(); }
    public List<String> getOptions() { return options; }
    public int getIndex() { return index; }
    public String getValue() { return options.get(index); }

    public void setIndex(int next) {
        this.index = Math.max(0, Math.min(options.size() - 1, next));
    }

    public void cycle() { index = (index + 1) % options.size(); }
    public void cycleBack() { index = (index + options.size() - 1) % options.size(); }
}
