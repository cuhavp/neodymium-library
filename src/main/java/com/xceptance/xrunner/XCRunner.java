package com.xceptance.xrunner;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.RunnerBuilder;
import org.junit.runners.model.TestClass;

import com.xceptance.multibrowser.Browser;
import com.xceptance.multibrowser.BrowserRunner;

public class XCRunner extends Runner
{
    List<List<Runner>> testRunner = new LinkedList<>();

    private TestClass testClass;

    private Description testDescription;

    private MethodExecutionContext methodExecutionContext;

    public XCRunner(Class<?> testKlass, RunnerBuilder rb) throws Throwable
    {
        List<List<Runner>> vectors = new LinkedList<>();
        testClass = new TestClass(testKlass);
        List<Runner> runners = new LinkedList<>();

        // find test vectors
        // scan for Browser and Parameters annotation
        // later on we could add handler for any annotation that should influence test run

        // lookup Browser annotation
        Browser browser = testClass.getAnnotation(Browser.class);
        if (browser != null)
        {
            runners.add(new BrowserRunner(testKlass));
        }

        methodExecutionContext = new MethodExecutionContext();

        // scan for JUnit Parameters
        List<FrameworkMethod> parameterMethods = testClass.getAnnotatedMethods(Parameters.class);
        if (parameterMethods.size() > 0)
        {
            setFinalStatic(Parameterized.class.getDeclaredField("DEFAULT_FACTORY"), new XCParameterRunnerFactory(methodExecutionContext));
            runners.add(new Parameterized(testKlass));
        }

        // collect children of ParentRunner sub classes
        doMagic(runners, vectors);

        // create method runners that actually execute the methods annotated with @Test
        List<Runner> methodVector = new LinkedList<>();
        for (FrameworkMethod method : testClass.getAnnotatedMethods(Test.class))
        {
            methodVector.add(new XCMethodRunner(testKlass, method, methodExecutionContext));
        }
        vectors.add(methodVector);

        testRunner = buildTestRunnerLists(vectors);
        testDescription = createTestDescription(testRunner, testClass);
    }

    private void setFinalStatic(Field field, Object newValue) throws Exception
    {
        field.setAccessible(true);

        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

        field.set(null, newValue);
    }

    private Description createTestDescription(List<List<Runner>> testRunner, TestClass testClass)
    {
        Description description = Description.createSuiteDescription(testClass.getJavaClass());

        for (List<Runner> runners : testRunner)
        {
            List<String> displayNames = new LinkedList<>();
            for (Runner runner : runners)
            {
                Description runnerDescription = runner.getDescription();
                String displayName = "";
                if (runner instanceof XCParameterRunner)
                {
                    displayName = ((XCParameterRunner) runner).getName();
                }
                else if (runner instanceof BlockJUnit4ClassRunner)
                {
                    displayName = runner.getDescription().getDisplayName();
                }
                else
                {
                    displayName = runnerDescription.getDisplayName();
                }

                displayNames.add(displayName);
            }

            // necessary to preserver JUnit view feature which lead you to the test method on double click the entry
            // https://github.com/eclipse/eclipse.jdt.ui/blob/0e4ddb8f4fd1d3c22748423acba36397e5f020e7/org.eclipse.jdt.junit/src/org/eclipse/jdt/internal/junit/ui/OpenTestAction.java#L108-L122
            Collections.reverse(displayNames);

            Description childDescription = Description.createTestDescription(testClass.getJavaClass(), String.join(" :: ", displayNames));
            description.addChild(childDescription);
        }

        return description;
    }

    private List<List<Runner>> buildTestRunnerLists(List<List<Runner>> vectors)
    {

        List<List<Runner>> runner = new LinkedList<>();
        runner.add(new LinkedList<>());

        // iterate over all vectors to build the cross product . Last vector should only consist of
        // method runners
        for (int i = vectors.size() - 1; i >= 0; i--)
        {
            List<List<Runner>> newTestRunners = new LinkedList<>();
            for (Runner r : vectors.get(i))
            {
                List<List<Runner>> testRunnerCopy = deepCopy(runner);
                for (List<Runner> list : testRunnerCopy)
                {
                    list.add(0, r);
                }
                newTestRunners.addAll(testRunnerCopy);
            }
            // overwrite previous list of runners
            runner = newTestRunners;
        }

        return runner;
    }

    private List<List<Runner>> deepCopy(List<List<Runner>> original)
    {
        List<List<Runner>> copy = new LinkedList<>();
        for (List<Runner> entry : original)
        {
            copy.add(new LinkedList<>(entry));
        }

        return copy;
    }

    @SuppressWarnings("unchecked")
    private void doMagic(List<Runner> runners, List<List<Runner>> vectors)
        throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException
    {
        // due to the mostly used protected modifier of getChildren method we have to do some magic here

        for (Runner runner : runners)
        {
            if (runner instanceof ParentRunner<?>)
            {
                Method m = runner.getClass().getDeclaredMethod("getChildren");
                if (m.getName().equals("getChildren"))
                {
                    if (!m.isAccessible())
                    {
                        m.setAccessible(true);
                    }
                    vectors.add((List<Runner>) m.invoke(runner));
                }
            }
        }
    }

    @Override
    public void run(RunNotifier notifier)
    {
        for (int i = 0; i < testRunner.size(); i++)
        {
            boolean firstIteration = (i == 0) ? true : false;
            boolean lastIteration = (i == testRunner.size() - 1) ? true : false;

            List<Runner> runners = testRunner.get(i);
            Description description = testDescription.getChildren().get(i);

            if (checkIgnored(runners))
            {
                notifier.fireTestIgnored(description);
            }
            else
            {
                Object classInstance;
                try
                {
                    classInstance = testClass.getOnlyConstructor().newInstance();
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }

                BrowserRunner browserRunner = null;
                notifier.fireTestStarted(description);
                for (int r = 0; r < runners.size(); r++)
                {
                    Runner runner = runners.get(r);

                    if (runner instanceof BrowserRunner)
                    {
                        // remember browser runner to close the web driver after test
                        browserRunner = (BrowserRunner) runner;
                    }


                    methodExecutionContext.setRunBeforeClass(firstIteration);
                    methodExecutionContext.setRunAfterClass(lastIteration);
                    methodExecutionContext.setRunnerDescription(description);
                    methodExecutionContext.setTestClassInstance(classInstance);

                    runner.run(notifier);
                }
                if (browserRunner != null)
                {
                    browserRunner.teardown();
                }
                notifier.fireTestFinished(description);
            }
        }
    }

    private boolean checkIgnored(List<Runner> runners)
    {
        for (Runner runner : runners)
        {
            if (runner instanceof XCMethodRunner)
            {
                XCMethodRunner methodRunner = (XCMethodRunner) runner;
                return (methodRunner.getChildren().get(0).getAnnotation(Ignore.class) != null);
            }
        }

        return false;
    }

    @Override
    public Description getDescription()
    {
        return testDescription;
    }
}
