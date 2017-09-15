package com.xceptance.xrunner;

import java.util.LinkedList;
import java.util.List;

import org.junit.internal.AssumptionViolatedException;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runner.notification.StoppedByUserException;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.MultipleFailureException;
import org.junit.runners.model.Statement;

public class XCMethodRunner extends BlockJUnit4ClassRunner
{

    List<FrameworkMethod> methodToRun;

    private Object testInstance;

    private MethodExecutionContext methodExecutionContext;

    private XCMethodRunner(Class<?> klass) throws InitializationError
    {
        super(klass);
    }

    public XCMethodRunner(Class<?> klass, FrameworkMethod method, MethodExecutionContext methodExecutionContext) throws InitializationError
    {
        super(klass);
        this.methodExecutionContext = methodExecutionContext;
        methodToRun = new LinkedList<>();
        methodToRun.add(method);
    }

    @Override
    protected List<FrameworkMethod> getChildren()
    {
        return methodToRun;
    }

    @Override
    public void run(RunNotifier notifier)
    {
        testInstance = methodExecutionContext.getTestClassInstance();

        XCRunListener xcr = new XCRunListener();
        try
        {
            RunNotifier subnotifier = new RunNotifier();
            subnotifier.addListener(xcr);

            Statement statement = childrenInvoker(subnotifier);

            if (methodExecutionContext.isRunBeforeClass())
            {
                statement = withBeforeClasses(statement);
            }

            if (methodExecutionContext.isRunAfterClass())
            {
                statement = withAfterClasses(statement);
            }
            statement.evaluate();
            if (xcr.hasFailure())
            {
                notifier.fireTestFailure(new Failure(methodExecutionContext.getRunnerDescription(), xcr.getFailure().getException()));
            }
        }
        catch (AssumptionViolatedException e)
        {
            notifier.fireTestAssumptionFailed(new Failure(methodExecutionContext.getRunnerDescription(), xcr.getFailure().getException()));
        }
        catch (StoppedByUserException e)
        {
            throw e;
        }
        catch (Throwable e)
        {
            List<Throwable> exceptionList = new LinkedList<>();
            if (e instanceof MultipleFailureException)
            {
                ((MultipleFailureException) e).getFailures();
            }
            else
            {
                exceptionList.add(e);
            }

            for (Throwable t : exceptionList)
            {
                notifier.fireTestFailure(new Failure(methodExecutionContext.getRunnerDescription(), t));
            }
        }
    }

    @Override
    protected Object createTest() throws Exception
    {
        return testInstance;
    }

    @Override
    public Description getDescription()
    {
        FrameworkMethod method = methodToRun.get(0);
        Description description = Description.createSuiteDescription(method.getName(), getRunnerAnnotations());
        return description;
    }
}
