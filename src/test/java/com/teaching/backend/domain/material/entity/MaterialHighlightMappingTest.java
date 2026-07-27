package com.teaching.backend.domain.material.entity;

import com.teaching.backend.domain.material.enums.HighlightType;
import jakarta.persistence.JoinColumn;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialHighlightMappingTest {

    @Test
    void materialHighlightUsesMaterialAnalysisAnchorOnly() throws Exception {
        assertThat(Arrays.stream(MaterialHighlight.class.getDeclaredFields())
                .anyMatch(field -> field.getType().equals(MaterialChunk.class)))
                .isFalse();

        var materialAnalysisField = MaterialHighlight.class.getDeclaredField("materialAnalysis");
        JoinColumn joinColumn = materialAnalysisField.getAnnotation(JoinColumn.class);

        assertThat(materialAnalysisField.getType()).isEqualTo(MaterialAnalysis.class);
        assertThat(joinColumn).isNotNull();
        assertThat(joinColumn.name()).isEqualTo("material_analysis_id");
        assertThat(joinColumn.nullable()).isFalse();
    }

    @Test
    void materialHighlightFactoryDoesNotAcceptMaterialChunk() {
        Method[] methods = MaterialHighlight.class.getDeclaredMethods();

        assertThat(Arrays.stream(methods)
                .filter(method -> method.getName().equals("create"))
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .anyMatch(parameterType -> parameterType.equals(MaterialChunk.class)))
                .isFalse();

        assertThat(Arrays.stream(methods)
                .filter(method -> method.getName().equals("create"))
                .anyMatch(method -> Arrays.equals(
                        method.getParameterTypes(),
                        new Class<?>[]{
                                MaterialAnalysis.class,
                                String.class,
                                HighlightType.class,
                                Integer.class,
                                Integer.class
                        }
                )))
                .isTrue();
    }
}
