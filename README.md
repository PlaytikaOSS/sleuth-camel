# Sleuth-camel library
If you are using camel along with spring boot 1.5.x version and willing to have your routes to be traced - this library is what you need.

## Usage
In order to integrate functionality provided by this library tou need to add following dependency:

```xml
<dependency>
   <artifactId>sleuth-camel-core</artifactId>
   <groupId>com.playtika.sleuth</groupId>
</dependency>
```
And that is pretty all, library contains spring boot auto-configuration which will do the magic with your camel context for you.
If for some reason integration should be disabled - just add following property:
```properties
spring.sleuth.camel.enabled=false
```