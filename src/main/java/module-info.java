module fusion.data {
    requires org.slf4j;
    requires net.bytebuddy;
    requires java.desktop;  // todo: remove!  only needed to un-camel-case property names
    exports zer0g.fusion.data;
}