package com.crime.detection.backend_service.engine;

import com.crime.detection.backend_service.model.Rule;
import com.crime.detection.backend_service.repository.RuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RuleEngine {

    @Autowired
    private RuleRepository ruleRepository;

    private final ExpressionParser parser = new SpelExpressionParser();

    public Rule evaluate(Map<String, Object> facts) {
        List<Rule> activeRules = ruleRepository.findByActiveTrue();
        StandardEvaluationContext context = new StandardEvaluationContext();
        facts.forEach(context::setVariable);

        for (Rule rule : activeRules) {
            try {
                Expression exp = parser.parseExpression(rule.getExpression());
                Boolean match = exp.getValue(context, Boolean.class);
                if (Boolean.TRUE.equals(match)) {
                    return rule;
                }
            } catch (Exception e) {
                System.err.println("Error parsing expression for rule " + rule.getName() + ": " + e.getMessage());
            }
        }
        return null;
    }
}
