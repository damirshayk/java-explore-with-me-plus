package ru.practicum.ewm.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.practicum.ewm.dto.comment.CommentDto;
import ru.practicum.ewm.dto.comment.NewCommentDto;
import ru.practicum.ewm.dto.comment.UpdateCommentRequest;
import ru.practicum.ewm.dto.user.UserShortDto;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CommentDtoTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeValidatorFactory() {
        FACTORY.close();
    }

    @ParameterizedTest
    @ValueSource(classes = {NewCommentDto.class, UpdateCommentRequest.class})
    void shouldRejectMissingAndBlankText(Class<?> dtoType) {
        assertThat(VALIDATOR.validateValue(dtoType, "text", null)).isNotEmpty();
        assertThat(VALIDATOR.validateValue(dtoType, "text", "")).isNotEmpty();
        assertThat(VALIDATOR.validateValue(dtoType, "text", " \t\n")).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(classes = {NewCommentDto.class, UpdateCommentRequest.class})
    void shouldAcceptTextAtLengthBoundaries(Class<?> dtoType) {
        assertThat(VALIDATOR.validateValue(dtoType, "text", "А")).isEmpty();
        assertThat(VALIDATOR.validateValue(dtoType, "text", "А".repeat(2000))).isEmpty();
        assertThat(VALIDATOR.validateValue(dtoType, "text", "А".repeat(2001))).isNotEmpty();
    }

    @Test
    void shouldReadRequestTextAndWriteResponseDates() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        assertThat(mapper.readValue("{\"text\":\"Хорошее событие\"}", NewCommentDto.class).getText())
                .isEqualTo("Хорошее событие");
        assertThat(mapper.readValue("{\"text\":\"Новый текст\"}", UpdateCommentRequest.class).getText())
                .isEqualTo("Новый текст");

        UserShortDto author = new UserShortDto();
        author.setId(5L);
        author.setName("Автор");
        CommentDto dto = new CommentDto();
        dto.setId(10L);
        dto.setText("Хорошее событие");
        dto.setAuthor(author);
        dto.setEventId(20L);
        dto.setCreatedOn(LocalDateTime.of(2026, 9, 17, 12, 0));

        JsonNode json = mapper.valueToTree(dto);
        assertThat(json.get("createdOn").asText()).isEqualTo("2026-09-17 12:00:00");
        assertThat(json.get("updatedOn").isNull()).isTrue();
        assertThat(json.get("author").get("name").asText()).isEqualTo("Автор");
        assertThat(json.get("author").has("email")).isFalse();
        assertThat(json.get("eventId").asLong()).isEqualTo(20L);

        dto.setUpdatedOn(dto.getCreatedOn().plusMinutes(5));
        assertThat(mapper.valueToTree(dto).get("updatedOn").asText()).isEqualTo("2026-09-17 12:05:00");
    }
}
