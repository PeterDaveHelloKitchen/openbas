package io.openex.helper;

import io.openex.database.model.*;
import io.openex.database.repository.AudienceRepository;
import io.openex.database.repository.DryInjectRepository;
import io.openex.database.repository.InjectRepository;
import io.openex.database.specification.DryInjectSpecification;
import io.openex.database.specification.InjectSpecification;
import io.openex.execution.ExecutableInject;
import io.openex.execution.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Stream.concat;

@Component
public class InjectHelper<T> {

    private InjectRepository<T> injectRepository;
    private DryInjectRepository<T> dryInjectRepository;
    private AudienceRepository audienceRepository;

    @Autowired
    public void setAudienceRepository(AudienceRepository audienceRepository) {
        this.audienceRepository = audienceRepository;
    }

    @Autowired
    public void setInjectRepository(InjectRepository<T> injectRepository) {
        this.injectRepository = injectRepository;
    }

    @Autowired
    public void setDryInjectRepository(DryInjectRepository<T> dryInjectRepository) {
        this.dryInjectRepository = dryInjectRepository;
    }

    public List<ExecutionContext> buildUsersFromInject(Injection<T> inject) {
        Exercise exercise = inject.getExercise();
        // Create stream from inject audiences
        Iterable<Audience> audiences = inject.isGlobalInject() ? audienceRepository.findAll() : inject.getAudiences();
        Stream<ExecutionContext> injectUserStream = StreamSupport.stream(audiences.spliterator(), false)
                .filter(Audience::isEnabled)
                .flatMap(audience -> audience.getUsers().stream()
                        .map(user -> new ExecutionContext(user, exercise, audience.getName())));
        // Create stream from animation group
        Stream<ExecutionContext> animationUserStream = exercise.getObservers().stream()
                .map(user -> new ExecutionContext(user, exercise, "Animation Group"));
        // Build result
        Stream<ExecutionContext> usersStream = concat(injectUserStream, animationUserStream);
        return usersStream
                .collect(groupingBy(ExecutionContext::getUser)).entrySet().stream()
                .map(entry -> new ExecutionContext(entry.getKey(), exercise,
                        entry.getValue().stream().flatMap(ua -> ua.getAudiences().stream()).toList()))
                .toList();
    }

    private boolean isInInjectableRange(Injection<?> injection) {
        Instant now = Instant.now();
        Instant start = now.minus(Duration.parse("PT1H"));
        Instant injectWhen = injection.getDate().orElseThrow();
        return injectWhen.isAfter(start) && injectWhen.isBefore(now);
    }

    @Transactional
    public List<ExecutableInject<T>> getInjectsToRun() {
        // Get injects
        List<Inject<T>> executableInjects = injectRepository.findAll(InjectSpecification.executable());
        Stream<ExecutableInject<T>> injects = executableInjects.stream()
                .filter(this::isInInjectableRange)
                .map(inject -> new ExecutableInject<>(inject, buildUsersFromInject(inject)));
        // Get dry injects
        List<DryInject<T>> executableDryInjects = dryInjectRepository.findAll(DryInjectSpecification.executable());
        Stream<ExecutableInject<T>> dryInjects = executableDryInjects.stream()
                .filter(this::isInInjectableRange)
                .map(inject -> new ExecutableInject<>(inject, buildUsersFromInject(inject)));
        // Combine injects and dry
        return concat(injects, dryInjects).collect(Collectors.toList());
    }
}
