# xpx-chain-txn
Simple Proximax Transactions Spammer Tool

# Compile
Get all dependencies, build and compile the archive (JAR).
```
# mvn clean install
```


# Edit proximax.properties:

```
# Network type
network=TEST_NET
# Node API
node=http://bctestnet1.brimstone.xpxsirius.io:3000
# sender private key
sender=<Private Key>
# Receiver address
receiver=<Address>
# XPX sending
xpx=1
# Transaction Fee: high, low, medium, zero
FeeCalculationStrategy=high
# text msg
msg=Hello world
```

# Run
Run the following and set up a cron job for it. 

```
# cd staging
# java -jar xpx-chain-txn-<VERSION>.jar 
```
