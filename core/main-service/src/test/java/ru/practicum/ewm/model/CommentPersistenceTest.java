package ru.practicum.ewm.model;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestConstructor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:comments;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CommentPersistenceTest {
    private static final LocalDateTime CREATED_ON = LocalDateTime.of(2026, 9, 17, 12, 0);
    private final TestEntityManager entityManager;
    private User author;
    private Event event;

    CommentPersistenceTest(TestEntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @BeforeEach
    void setUp() {
        User initiator = new User();
        initiator.setName("Организатор");
        initiator.setEmail("initiator@example.org");
        entityManager.persist(initiator);

        author = new User();
        author.setName("Автор комментария");
        author.setEmail("author@example.org");
        entityManager.persist(author);

        Category category = new Category();
        category.setName("Концерты");
        entityManager.persist(category);

        event = new Event();
        event.setAnnotation("Описание концерта для проверки комментариев");
        event.setDescription("Подробное описание концерта для проверки комментариев");
        event.setTitle("Концерт");
        event.setCategory(category);
        event.setInitiator(initiator);
        event.setCreatedOn(CREATED_ON.minusDays(1));
        event.setEventDate(CREATED_ON.plusDays(1));
        event.setLocation(new Location(55.75F, 37.62F));
        event.setState(EventState.PUBLISHED);
        event.setPublishedOn(CREATED_ON);
        entityManager.persistAndFlush(event);
    }

    @Test
    void shouldPersistUpdateAndDeleteCommentWithoutDeletingAuthorOrEvent() {
        Comment comment = newComment();
        comment.setText("А".repeat(2000));
        entityManager.persistAndFlush(comment);
        Long commentId = comment.getId();
        Long authorId = author.getId();
        Long eventId = event.getId();
        entityManager.clear();

        Comment found = entityManager.find(Comment.class, commentId);
        assertThat(found.getText()).hasSize(2000);
        assertThat(found.getAuthor().getId()).isEqualTo(authorId);
        assertThat(found.getEvent().getId()).isEqualTo(eventId);
        assertThat(found.getCreatedOn()).isEqualTo(CREATED_ON);
        assertThat(found.getUpdatedOn()).isNull();

        found.setText("Обновлённый комментарий");
        found.setUpdatedOn(CREATED_ON.plusMinutes(5));
        entityManager.flush();
        entityManager.clear();
        Comment updated = entityManager.find(Comment.class, commentId);
        assertThat(updated.getText()).isEqualTo("Обновлённый комментарий");
        assertThat(updated.getUpdatedOn()).isEqualTo(CREATED_ON.plusMinutes(5));
        entityManager.remove(updated);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(Comment.class, commentId)).isNull();
        assertThat(entityManager.find(User.class, authorId)).isNotNull();
        assertThat(entityManager.find(Event.class, eventId)).isNotNull();
    }

    @Test
    void shouldDeleteOnlyCommentsOfDeletedAuthor() {
        Comment removed = entityManager.persistAndFlush(newComment());
        Comment retained = newComment();
        retained.setAuthor(event.getInitiator());
        entityManager.persistAndFlush(retained);

        entityManager.remove(author);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(Comment.class, removed.getId())).isNull();
        assertThat(entityManager.find(Comment.class, retained.getId())).isNotNull();
        assertThat(entityManager.find(Event.class, event.getId())).isNotNull();
    }

    @Test
    void shouldDeleteCommentsWhenEventIsDeleted() {
        Comment comment = entityManager.persistAndFlush(newComment());
        entityManager.remove(event);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(Comment.class, comment.getId())).isNull();
        assertThat(entityManager.find(User.class, author.getId())).isNotNull();
    }

    @Test
    void shouldRejectCommentWithoutAuthor() {
        Comment comment = newComment();
        comment.setAuthor(null);
        assertThatThrownBy(() -> entityManager.persistAndFlush(comment))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("AUTHOR_ID");
    }

    @Test
    void shouldRejectCommentWithMissingEvent() {
        Comment comment = newComment();
        comment.setEvent(entityManager.getEntityManager().getReference(Event.class, Long.MAX_VALUE));
        assertThatThrownBy(() -> entityManager.persistAndFlush(comment))
                .isInstanceOf(ConstraintViolationException.class);
    }

    private Comment newComment() {
        Comment comment = new Comment();
        comment.setText("Интересное событие");
        comment.setAuthor(author);
        comment.setEvent(event);
        comment.setCreatedOn(CREATED_ON);
        return comment;
    }
}
