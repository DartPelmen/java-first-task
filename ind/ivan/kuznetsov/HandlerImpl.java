package ind.ivan.ivankuznetsov;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.time.Duration;


public class HandlerImpl implements Handler {
    
    CompletableFuture<ApplicationStatusResponse> taskByApplication1;
    CompletableFuture<ApplicationStatusResponse> taskByApplication2;
    private static Logger logger = Logger.getLogger(HandlerImpl.class.getSimpleName());
    private static int retriesCount = 0;
    private ExecutorService service;
    public final Client client;
    public HandlerImpl(Client client) {
        service = Executors.newFixedThreadPool(2);
        this.client = client;
    }

    @Override
    public ApplicationStatusResponse performOperation(String id) {
    

        ApplicationStatusResponse requestResult;
            var requestTime = System.currentTimeMillis();

            taskByApplication1 = CompletableFuture.supplyAsync(()->{
                Response resp = client.getApplicationStatus1(id);
                synchronized(this){
                    if(taskByApplication2 != null){
                        taskByApplication2.cancel(true);
                    }
                    return resp;
                }
            }, service).thenApply(response->{
                return parseResult(id, System.currentTimeMillis() - requestTime, response);
            });

            taskByApplication2 = CompletableFuture.supplyAsync(()->{
                Response resp = client.getApplicationStatus1(id);
                synchronized(this){
                    if(taskByApplication1 != null){
                        taskByApplication1.cancel(true);
                    }
                    return resp;
                }
            }, service).thenApply(response->{
                return parseResult(id, System.currentTimeMillis() - requestTime, response);
            });
            try {
                return taskByApplication1.get(15,TimeUnit.SECONDS);

            } catch (InterruptedException | ExecutionException | TimeoutException e) {

                logger.log(Level.INFO, "catch exeption for first application", e);
            }
            try {
                return taskByApplication2.get(15,TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                logger.log(Level.INFO, "catch exeption for second application", e);
            }

    }
    
    private ApplicationStatusResponse retryAfterDelay(String id, long delay){
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            logger.log(Level.FINER, "sleep exception", e);
        }
        retriesCount++;
        return performOperation(id);
    }
    
    private ApplicationStatusResponse parseResult(String id, long requestTime, Response response){
        return switch (response) {
            case Response.Success success -> new ApplicationStatusResponse.Success(success.applicationId(),success.applicationStatus());
            case Response.Failure fail ->  new ApplicationStatusResponse.Failure(Duration.ofMillis(requestTime), retriesCount++);
            
            case Response.RetryAfter retry -> retryAfterDelay(id, retry.delay().toMillis());
            default -> null;
        };
    }
}
