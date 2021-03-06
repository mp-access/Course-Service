package ch.uzh.ifi.access.student.model;

import ch.uzh.ifi.access.course.model.Rounding;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("unused")
@Value
@Data
@Builder
public class SubmissionEvaluation {

    public static SubmissionEvaluation NO_SUBMISSION = new SubmissionEvaluation(new Points(0, 0), 0, Instant.MIN,
            Collections.emptyList(), Rounding.DEFAULT);

    private Points points;

    private double maxScore;

    private Instant timestamp;

    private List<String> hints;

    private Rounding rounding;

    @JsonProperty
    public boolean hasSubmitted() {
        return !NO_SUBMISSION.equals(this);
    }

    public double getScore() {
        double score;
        if (points.getMax() == 0) {
            score = 0.0;
        } else if (rounding == null) {
            score = Rounding.DEFAULT.round((points.getCorrect() / (double) points.getMax() * maxScore));
        } else {
            score = rounding.round((points.getCorrect() / (double) points.getMax() * maxScore));
        }
        return Math.min(score, maxScore);
    }

    public List<String> getHints() {
        return hints != null && hints.size() > 1 ? List.of(hints.get(0)) : hints;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Points {
        private int correct;
        private int max;

        public boolean isEverythingCorrect() {
            return correct == max;
        }
    }
}
