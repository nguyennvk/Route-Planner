## Important
1. Check that every query is on index
2. Make sure that user_id and email is lowercase
3. Add version to table when applicable
4. Using OSRM for distance matrix over GraphHopper because it is faster and free.
However, the limit is that i need to prepare the map for each kind vehicle (to be look at later)
5. Twist the planner algorithm for better result
6. Handle int, bigdecimal, decimal miss match for weight, m3. Important type Double vs Long in OsrmTableResponse
7. Miss matching in gram and kilo gram.
8. Make sure to add constrain for not passing the shift.
9. Add the reverse geocoding
10. Create on the go. Create customer on the go, item enable searching.
11. Fix the route so that it can have flexible start date. It cannot wait long time at the a location for the strict time window
12. Add hover effect for point on the map
13. Fix the route on the map.
14. Add assign button on route plan so that the driver can see their route.
## What I learn
1. The N + 1 problem (sovle by join fetch which preload the reference table)
2. save vs saveAndFlush. Hibernate prioritize the read and write will be store in a place where i only flush when the Transactional is done. That is why save will return the old (before the change) value. You need to flush to immediately see the changes. saveAndFlush is used when you read the value right after you write.
