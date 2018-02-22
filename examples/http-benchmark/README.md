Commands
---

There are a variety of commands that are available for testing different types of uploads. These commands also use [dropwizard metrics](https://github.com/dropwizard/metrics) to time each individual request. These metrics are written out as a CSV file at the end fo the test run. 
```
09:28:22 $ java -jar http2-client-1.0-SNAPSHOT.jar
usage: <command> [<args>]

The most commonly used benchmark commands are:
    payload   Tests writing to an HTTP endpoint writing a payload at a time with a number of entities
    single    Tests writing to an HTTP endpoint writing a single message per request
    stream    Streams a number of entities to a rest end point as MIME multipart/mixed
    
See 'help <command>' for more information on a specific command.    
```

For example, to see the options available for the `single` command:
```
09:35:21 $ java -jar http2-client-1.0-SNAPSHOT.jar help single
NAME
        benchmark single - Tests writing to an HTTP endpoint writing a single
        message per request

SYNOPSIS
        benchmark single
                [(-b <BEARER_TOKEN_LOCATION> | --bearer <BEARER_TOKEN_LOCATION>)]
                [(-e <numEntities> | --entities <numEntities>)] [(-h2 | http2)]
                [(-o <resultsOutputDir> | --output <resultsOutputDir>)]
                [(-r <repetitions> | --repetitions <repetitions>)]
                [(-t <threads> | --threads <threads>)] [(-u <url> | --url <url>)]
                [(-v <payloadSize> | --size <payloadSize>)]

OPTIONS
        -b <BEARER_TOKEN_LOCATION>, --bearer <BEARER_TOKEN_LOCATION>
            the bearer token set in the Authorization header

        -e <numEntities>, --entities <numEntities>
            number of entities to upload; default uploads in the following
            increments: 1, 10, 100, 1000, 5000

        -h2, http2
            indicates to use http2

        -o <resultsOutputDir>, --output <resultsOutputDir>
            file to write results into

        -r <repetitions>, --repetitions <repetitions>
            tells command how many times to repeat the benchmark

        -t <threads>, --threads <threads>
            number of threads to execute in parallel

        -u <url>, --url <url>
            url to hit for http tests

        -v <payloadSize>, --size <payloadSize>
            size of each entity during the upload
```

All the commands have these same options when running. Specifying `-h2` indicates to use http/2 (see project [readme](../README.md) if using http/2) for requests. 
 
 Example run command:
 ```
 ava -jar -Xbootclasspath/p:alpn-boot-8.1.9.v20160720.jar http2-client-1.0-SNAPSHOT.jar stream -u https://10.190.111.80:8445/http2 -h2 -r 5 -t 2 -e 50 -v 2000 -o stream_http2_results.csv
 ```
 
