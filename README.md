Amazon EC2 pricing table generator
==================================

A tool for generating EC2 pricing matrix into excel file. Supports all regions excluding BJS.

### Pre-Condiction
You have to create a DynamoDB table named: EC2PricingTable (primary key = Id) in us-east-1.

### How to run
./bin/activator 'run ap-northeast-1' --> Generate us-east-1 pricing table in /tmp
./bin/activator 'run ALL' --> Generate all regions' pricing table in /tmp