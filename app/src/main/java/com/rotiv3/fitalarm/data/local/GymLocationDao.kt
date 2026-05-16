package com.rotiv3.fitalarm.data.local

import androidx.room.*
import com.rotiv3.fitalarm.data.model.GymLocation
import kotlinx.coroutines.flow.Flow

@Dao
interface GymLocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(gymLocation: GymLocation): Long

    @Update
    suspend fun update(gymLocation: GymLocation)

    @Delete
    suspend fun delete(gymLocation: GymLocation)

    @Query("DELETE FROM gym_locations WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM gym_locations ORDER BY name ASC")
    fun getAll(): Flow<List<GymLocation>>

    @Query("SELECT * FROM gym_locations WHERE isActive = 1 ORDER BY name ASC")
    fun getActiveLocations(): Flow<List<GymLocation>>

    @Query("SELECT * FROM gym_locations WHERE isActive = 1")
    suspend fun getActiveLocationsList(): List<GymLocation>

    @Query("SELECT * FROM gym_locations WHERE id = :id")
    suspend fun getById(id: Int): GymLocation?

    @Query("UPDATE gym_locations SET isActive = :isActive WHERE id = :id")
    suspend fun setActive(id: Int, isActive: Boolean)
}
