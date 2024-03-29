package life.genny.qwandaq;


/*
 * This enum is designed for simplicity and speed. 
 * The database will be expected to default to ACTIVE initially and we'll slowly bring in theother statuses
 */
public enum EEntityStatus {

	ACTIVE(0),          // 0 - This status means that the entity should be picked up in all database fetches
	TEST(1),            // 1 - This means that the entity is a well crafted test entity for use by devs
	TEMPORARY(2),       // 2 - This means that the entity is a useful and persistent test object
	PENDING(3),         // 3 - This means that the entity is currently under construction and cannot be fetched under normal operation
	DISABLED(4),        // 4 - This means that the entity is currently not to be fetched, but is expected to possibly be returned to active status
	ARCHIVED(5),        // 5 - This means that the entity is not to be deleted , but is archived but not visible to normal operation
	PENDING_DELETE(6),  // 6 - This means that the entity is pending deletion, but not yet deleted
	DELETED(7);         // 7 - This means that the entity is marked deleted
	
	private Integer status=0;
		
	private EEntityStatus(final Integer status) {
		this.status = status;
	}

	public final Integer getStatus() {
		return status;
	}

	public void setStatus(Integer status) {
		this.status = status;
	}
  

	public EEntityStatus valueOf(Integer value)
	{
		for (EEntityStatus enumValue : EEntityStatus.values()) {
			if (enumValue.getStatus().equals(value)) {
				return enumValue;
			}

		}
		return null;
	}

}
