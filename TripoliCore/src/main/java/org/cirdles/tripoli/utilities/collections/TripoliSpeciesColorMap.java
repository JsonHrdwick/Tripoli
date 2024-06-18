package org.cirdles.tripoli.utilities.collections;

import org.cirdles.tripoli.expressions.species.SpeciesRecordInterface;
import org.cirdles.tripoli.species.SpeciesColors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.*;

import static org.cirdles.tripoli.constants.TripoliConstants.TRIPOLI_DEFAULT_HEX_COLORS;

public class TripoliSpeciesColorMap implements Map<SpeciesRecordInterface, SpeciesColors>, Serializable {
    private final Map<SpeciesRecordInterface, SpeciesColors> mapOfSpeciesToColors;
    private int index;

    public TripoliSpeciesColorMap() {
        super();
        mapOfSpeciesToColors = Collections.synchronizedSortedMap(new TreeMap<>());
    }

    @Override
    public int size() {
        return mapOfSpeciesToColors.size();
    }

    @Override
    public boolean isEmpty() {
        return mapOfSpeciesToColors.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return mapOfSpeciesToColors.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return mapOfSpeciesToColors.containsValue(value);
    }

    @Override
    public SpeciesColors get(Object key) {
        if (key instanceof SpeciesRecordInterface) {
            SpeciesRecordInterface speciesRecordInterfaceKey = (SpeciesRecordInterface) key;
            if (!containsKey(key)) {
                put(speciesRecordInterfaceKey,
                        new SpeciesColors(
                                TRIPOLI_DEFAULT_HEX_COLORS.get(index * 4),
                                TRIPOLI_DEFAULT_HEX_COLORS.get(index * 4 + 1),
                                TRIPOLI_DEFAULT_HEX_COLORS.get(index * 4 + 2),
                                TRIPOLI_DEFAULT_HEX_COLORS.get(index * 4 + 3)
                        ));
                index++;
            }
        }
        return mapOfSpeciesToColors.get(key);
    }

    @Nullable
    @Override
    public SpeciesColors put(SpeciesRecordInterface key, SpeciesColors value) {
        return mapOfSpeciesToColors.put(key,value);
    }

    @Override
    public SpeciesColors remove(Object key) {
        return mapOfSpeciesToColors.remove(key);
    }

    @Override
    public void putAll(@NotNull Map<? extends SpeciesRecordInterface, ? extends SpeciesColors> m) {
        m.forEach((this::put));
    }

    @Override
    public void clear() {
        mapOfSpeciesToColors.clear();
    }

    @NotNull
    @Override
    public Set<SpeciesRecordInterface> keySet() {
        return mapOfSpeciesToColors.keySet();
    }

    @NotNull
    @Override
    public Collection<SpeciesColors> values() {
        return mapOfSpeciesToColors.values();
    }

    @NotNull
    @Override
    public Set<Entry<SpeciesRecordInterface, SpeciesColors>> entrySet() {
        return mapOfSpeciesToColors.entrySet();
    }
}
