package com.crime.detection.backend_service.config;

import com.crime.detection.backend_service.model.Account;
import com.crime.detection.backend_service.model.Rule;
import com.crime.detection.backend_service.repository.AccountRepository;
import com.crime.detection.backend_service.repository.RuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.List;

@Configuration
public class DatabaseInitializer implements CommandLineRunner {

    @Autowired
    private RuleRepository ruleRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Override
    public void run(String... args) throws Exception {
        // Initialize Default Compliance Rules
        if (ruleRepository.count() == 0) {
            Rule rule1 = new Rule();
            rule1.setName("High Value Risk Hold");
            rule1.setDescription("Freeze transactions with a risk score over 85 and amount greater than 10,000");
            rule1.setExpression("#riskScore > 85.0 && #amount > 10000.0");
            rule1.setAction("HOLD");
            rule1.setActive(true);

            Rule rule2 = new Rule();
            rule2.setName("Geographic Velocity Alert");
            rule2.setDescription("Escalate when country mismatch and high velocity spikes occur together");
            rule2.setExpression("#country_mismatch > 30.0 && #velocity_spike > 40.0");
            rule2.setAction("ESCALATE");
            rule2.setActive(true);

            Rule rule3 = new Rule();
            rule3.setName("Extreme Velocity Spike");
            rule3.setDescription("Escalate when velocity spike is abnormally high (> 80%)");
            rule3.setExpression("#velocity_spike > 80.0");
            rule3.setAction("ESCALATE");
            rule3.setActive(true);

            ruleRepository.saveAll(List.of(rule1, rule2, rule3));
            System.out.println("Default compliance rules initialized.");
        }

        // Initialize mock accounts for graph representation
        if (accountRepository.count() == 0) {
            String[] names = {"Alice Smith", "Bob Jones", "Charlie Brown", "Diana Prince", "Evan Wright"};
            String[] accounts = {"ACC-001", "ACC-002", "ACC-003", "ACC-004", "ACC-005"};
            String[] countries = {"US", "UK", "HighRiskCountry", "CA", "US"};

            for (int i = 0; i < accounts.length; i++) {
                Account acc = new Account();
                acc.setId(accounts[i]);
                acc.setAccountNumber(accounts[i]);
                acc.setCustomerName(names[i]);
                acc.setCountry(countries[i]);
                acc.setRiskRating("LOW");
                acc.setCreatedAt(LocalDateTime.now().minusMonths(6));
                accountRepository.save(acc);
            }
            System.out.println("Mock accounts initialized.");
        }
    }
}
