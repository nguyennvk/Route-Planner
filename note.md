## Important
1. Check that every query is on index
2. Make sure that user_id and email is lowercase
3. Add version to table when applicable

## What I learn
1. The N + 1 problem (sovle by join fetch which preload the reference table)
2. save vs saveAndFlush. Hibernate prioritize the read and write will be store in a place where i only flush when the Transactional is done. That is why save will return the old (before the change) value. You need to flush to immediately see the changes. saveAndFlush is used when you read the value right after you write.