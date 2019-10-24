# Java - List Conference Participants Tutorial

This project serves as a guide to help you build an application with FreeClimb. Specifically, the project will:

- List the participants of the specified conference

## Setting up your new app within your FreeClimb account

To get started using a FreeClimb account, follow the instructions [here](https://persephony-docs.readme.io/docs/getting-started-with-persephony).

## Setting up the Tutorial

1. Configure environment variables.

   | ENV VARIABLE | DESCRIPTION                                                                                                                               |
   | ------------ | ----------------------------------------------------------------------------------------------------------------------------------------- |
   | ACCOUNT_ID   | Account ID which can be found under [API Keys](https://www.persephony.com/dashboard/portal/account/authentication) in Dashboard           |
   | AUTH_TOKEN   | Authentication Token which can be found under [API Keys](https://www.persephony.com/dashboard/portal/account/authentication) in Dashboard |
   |  HOST        |  The host url where your application is hosted (e.g. yourHostedApp.com)                                                                   |

2. Provide a value for the variable `agentPhoneNumber` which is a verified phone number to be called. To learn more about verified phone numbers go [here](https://docs.persephony.com/docs/connecting-calls).

## Building and Runnning the Tutorial

1. Build and run the application using command:

   ```bash
   $ gradle build && java -Dserver.port=3000 -jar build/libs/gs-spring-boot-0.1.0.jar
   ```
