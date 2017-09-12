package com.xceptance.xrunner;

import java.util.LinkedList;
import java.util.List;

import org.junit.internal.AssumptionViolatedException;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runner.notification.StoppedByUserException;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

public class XCMethodRunner extends BlockJUnit4ClassRunner implements ITestClassInjector
{

    List<FrameworkMethod> methodToRun;

    private Object testInstance;

    private XCMethodRunner(Class<?> klass) throws InitializationError
    {
        super(klass);
    }

    public XCMethodRunner(Class<?> klass, FrameworkMethod method) throws InitializationError
    {
        super(klass);
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
        // super.run(notifier);
        try
        {
            Statement statement = classBlock(notifier);
            statement.evaluate();
        }
        catch (AssumptionViolatedException e)
        {
        }
        catch (StoppedByUserException e)
        {
            throw e;
        }
        catch (Throwable e)
        {
        }
    }

    @Override
    protected Object createTest() throws Exception
    {
        return testInstance;
    }

    @Override
    public void setTestClass(Object instance)
    {
        testInstance = instance;
    }

    @Override
    public Description getDescription()
    {
        FrameworkMethod method = methodToRun.get(0);
        Description description = Description.createSuiteDescription(method.getName(), getRunnerAnnotations());
        return description;
    }

}