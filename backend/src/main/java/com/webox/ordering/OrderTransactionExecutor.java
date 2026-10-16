package com.webox.ordering;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.dao.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import com.webox.shared.ApiException;
@Component
public class OrderTransactionExecutor {
    private final TransactionTemplate transaction;private final com.webox.shared.OperationalMetrics metrics;
    public OrderTransactionExecutor(PlatformTransactionManager manager,com.webox.shared.OperationalMetrics metrics) {
        this.metrics=metrics;
        transaction=new TransactionTemplate(manager);transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public <T> T execute(Supplier<T> operation,Supplier<T> uniqueConflict) {
        for(int attempt=0;attempt<3;attempt++) {
            try { return transaction.execute(status->operation.get()); }
            catch(DuplicateKeyException conflict) {
                // TransactionTemplate has fully rolled back before conflict resolution starts.
                if(uniqueConflict==null) throw conflict;return transaction.execute(status->uniqueConflict.get());
            } catch(PessimisticLockingFailureException conflict) {
                if(attempt==2) throw unavailable();metrics.lockRetry();java.util.concurrent.locks.LockSupport.parkNanos((attempt+1)*10_000_000L);
            }
        }
        throw unavailable();
    }
    public static ApiException unavailable() { return new ApiException(503,"TEMPORARY_UNAVAILABLE","The order could not be completed yet. Retry with the same request.",true,Map.of()); }
}
