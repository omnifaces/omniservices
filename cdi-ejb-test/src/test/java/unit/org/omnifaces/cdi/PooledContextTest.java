package unit.org.omnifaces.cdi;

import javax.annotation.PostConstruct;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.omnifaces.cdi.pooled.Pooled;

@RunWith(CdiTestRunner.class)
public class PooledContextTest {

    @Inject
    private Instance<MainPooledBean> beanInstance;
    
    @Test
    public void hello() {
        MainPooledBean mainBean = beanInstance.get();
        assertThat(mainBean.callChildBean(), is(equalTo("MainPooledBean 1, child: NestedPooledBean 1")));
    }
    
    @Pooled
    public static class MainPooledBean {
        
        private static int counter = 0;
        
        @Inject
        private NestedPooledBean child;
        
        @Inject
        private NestedPooledBean child2;
        
        @PostConstruct
        public void init() {
            counter++;
        }
        
        public String callChildBean() {
            return MainPooledBean.class.getSimpleName() + " " + counter 
                    + ", children: " + child.call() + ", " + child2.call();
        }
    }

    @Pooled
    public static class NestedPooledBean {
        private static int counter = 0;
        
        @PostConstruct
        public void init() {
            counter++;
        }
        
        public String call() {
            return NestedPooledBean.class.getSimpleName() + " " + counter;
        }
    }
}
