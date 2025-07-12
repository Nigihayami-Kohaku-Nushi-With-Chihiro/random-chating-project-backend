package random.chating.org.randomchatingproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import random.chating.org.randomchatingproject.entity.ChatRoom;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, String> {

    @Query("SELECT cr FROM ChatRoom cr WHERE cr.user1Id = :userId OR cr.user2Id = :userId")
    List<ChatRoom> findByUserId(@Param("userId") Long userId);

    @Query("SELECT cr FROM ChatRoom cr WHERE (cr.user1Id = :user1Id AND cr.user2Id = :user2Id) OR (cr.user1Id = :user2Id AND cr.user2Id = :user1Id)")
    Optional<ChatRoom> findByUser1IdAndUser2Id(@Param("user1Id") Long user1Id, @Param("user2Id") Long user2Id);

    @Modifying
    @Query("DELETE FROM ChatRoom cr WHERE cr.user1Id = :userId OR cr.user2Id = :userId")
    void deleteByUser1IdOrUser2Id(@Param("userId") Long userId, @Param("userId") Long userId2);
}