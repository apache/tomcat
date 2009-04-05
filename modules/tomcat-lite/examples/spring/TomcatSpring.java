

import java.io.IOException;

import javax.servlet.ServletException;

import org.apache.tomcat.integration.ObjectManager;
import org.apache.tomcat.lite.TomcatLite;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;

/**
 * Example of embedding tomcat-lite in Spring. 
 * 
 * Spring is a good example because it has some limitations - it can't 
 * inject into existing (user-created) objects, which would have been
 * nice and easier for tomcat-lite. As a result, things are harder and more 
 * verbose than they should.  
 * 
 * This is just an example - I'm not an expert in spring and I wouldn't 
 * use it, too verbose and bloated - while still missing existing-objects
 * injection ( at least for regular objects ).
 * 
 * This should also work with small modifications for jboss microcontainer, 
 * and probably other frameworks that support POJO. 
 * 
 * @author Costin Manolache
 */
public class TomcatSpring {

    void loadProperties() throws IOException, ServletException {
        final DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        
        // Properties won't work - no support for list.
        // PropertiesBeanDefinitionReader rdr = 
        XmlBeanDefinitionReader rdr = new XmlBeanDefinitionReader(factory);
        rdr.loadBeanDefinitions("tomcat-spring.xml");
        
        TomcatLite lite = (TomcatLite) factory.getBean("TomcatLite");
        
        lite.setObjectManager(new ObjectManager() {
            public Object get(String name) {
                int lastDot = name.lastIndexOf(".");
                if (lastDot > 0) {
                    name = name.substring(lastDot + 1);
                }
                return factory.getBean(name);
            }
        });

        lite.run();
    }
    
    public static void main(String[] args) throws IOException, ServletException {
        new TomcatSpring().loadProperties();
    }
}
