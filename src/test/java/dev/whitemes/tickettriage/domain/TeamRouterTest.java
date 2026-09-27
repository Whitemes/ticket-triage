package dev.whitemes.tickettriage.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Every category must map to a non-null, non-blank team name. */
class TeamRouterTest {

    private final TeamRouter router = new TeamRouter();

    @Test
    void allCategoriesMappedToATeam() {
        for (Category category : Category.values()) {
            String team = router.route(category);
            assertThat(team)
                    .as("Team for category %s", category)
                    .isNotNull()
                    .isNotBlank();
        }
    }
}
