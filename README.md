[![CircleCI](https://circleci.com/gh/Playtika/sleuth-camel/tree/develop.svg?style=shield&circle-token=95c3efe67fa904c07172b23971bff7ffbff8798b)](https://circleci.com/gh/Playtika/sleuth-camel/tree/develop)
[![codecov](https://codecov.io/gh/Playtika/sleuth-camel/branch/develop/graph/badge.svg)](https://codecov.io/gh/Playtika/sleuth-camel)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/65b4c1b566844db99f3fc569a57ad36d)](https://www.codacy.com/app/PlaytikaGithub/sleuth-camel?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=Playtika/sleuth-camel&amp;utm_campaign=Badge_Grade)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.playtika.sleuth/sleuth-camel/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.playtika.sleuth/sleuth-camel)








# Sleuth-camel library
If you are using camel along with spring boot and willing to have your routes to be traced - this library is what you need.

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

For boot 1.5.x use 1.x version, for Spring boot 2.x.x version - sleuth-camel-core 2.x version should be used.