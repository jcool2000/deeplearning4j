package org.deeplearning4j.datasets.iterator.parallel;

import org.nd4j.linalg.dataset.api.DataSet;
import org.nd4j.linalg.dataset.api.iterator.enums.InequalityHandling;
import org.nd4j.linalg.exception.ND4JIllegalStateException;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author raver119@gmail.com
 */
public abstract class BaseParallelDataSetIterator {
    protected AtomicLong counter = new AtomicLong(0);

    protected InequalityHandling inequalityHandling;
    protected int numProducers;

    protected AtomicBoolean allDepleted = new AtomicBoolean(false);
    protected MultiBoolean states;
    protected MultiBoolean resetTracker;


    protected BaseParallelDataSetIterator(int numProducers) {
        states = new MultiBoolean(numProducers, true);
        resetTracker = new MultiBoolean(numProducers, false, true);
    }


    public boolean hasNext() {
        // if all producers are depleted - there's nothing to do here then
        if (states.allFalse() || allDepleted.get())
            return false;

        int curIdx = getCurrentProducerIndex();

        boolean hasNext = hasNextFor(getCurrentProducerIndex());
        states.set(hasNext, getCurrentProducerIndex());

        if (states.allFalse())
            return false;

        if (hasNext)
            return true;

        switch (inequalityHandling) {
            // FIXME: RESET should be applicable ONLY to producers which return TRUE for resetSupported();
            case RESET: {
                    resetTracker.set(true, getCurrentProducerIndex());

                    // we don't want to have endless loop here, so we only do reset until all producers depleted at least once
                    if (resetTracker.allTrue()) {
                        allDepleted.set(true);
                        return false;
                    }

                    reset(getCurrentProducerIndex());

                    // triggering possible adsi underneath
                    hasNextFor(getCurrentProducerIndex());

                    return true;
                }
            case RELOCATE: {
                    // TODO: transparent switch to next producer should happen here
                    while (!hasNext) {
                        stepForward();
                        hasNext = hasNextFor(getCurrentProducerIndex());
                        states.set(hasNext, getCurrentProducerIndex());

                        if (states.allFalse())
                            return false;
                    }

                    return true;
                }
            case PASS_NULL: {
                    // we just return true here, no matter what's up
                    return true;
                }
            case STOP_EVERYONE: {
                    if (!states.allTrue())
                        return false;

                    return true;
                }
            default:
                throw new ND4JIllegalStateException("Unknown InequalityHanding option was passed in: " + inequalityHandling);
        }
    }

    public DataSet next() {
        DataSet ds = nextFor(getCurrentProducerIndex());
        stepForward();
        return ds;
    }

    protected int getCurrentProducerIndex() {
        return (int)(counter.get() % numProducers);
    }

    protected void stepForward() {
        counter.getAndIncrement();
    }

    public abstract boolean hasNextFor( int consumer);

    public abstract DataSet nextFor(int consumer);

    protected abstract void reset(int consumer);
}
