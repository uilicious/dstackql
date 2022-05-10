# DStackQL

Central Data Stack API Service.

Used to provide a central dataservice level, to be used by an API application. Acting as the standardised API gateway for the specific data backend.

This is based on the picoded/JavaCommons-dstack service

## Project goals & status

Project is currently in its initial setup / experimentation phases. The goal is to support the following

- Full GraphQL support for easy object query/updates
- Standard CRUD API
- Client connector to DStack project, allowing a more seemless transition for existing API to shared API.
- Support for extensions via code for ...
    - parametised role restrictions for queries
    - alternative dstack implementations 

What is not the scope of this project

- Connection security, anyone with the basic auth connection to this service has full access to all data service
    - Parametised role, is used to facilitate data service restrictions for the API

## Future Considerations to support

- Support the IN where clause
- Create a standard java query builder ?
- Consider making the dstack aware of its internal namespaces (a tracking table?)
- Upgrade RunnableTaskCluster to be able to support "multiple threads"
- Implement a DStack Worker Queue data structure / module

## Immediate @TODO

- [x] Basic Project Setup
- [ ] Define baseline example config
- [ ] Implement the config stack initialization
- [ ] Implement the single DataObjectMap preloader logic
- [ ] Implement the single thread DataObjectMap preloader

- [ ] Implement the core DStack initialization
- [ ] Implement the background worker logic