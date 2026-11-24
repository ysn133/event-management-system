/** CLI entry point for the unmodified official JMeter 5.6.3 Maven libraries. */
public class JMeterCli {
    public static void main(String[] args) {
        new org.apache.jmeter.JMeter().start(args);
    }
}
