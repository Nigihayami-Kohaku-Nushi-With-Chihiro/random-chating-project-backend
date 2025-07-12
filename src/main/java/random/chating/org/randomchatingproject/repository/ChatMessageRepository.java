package random.chating.org.randomchatingproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import random.chating.org.randomchatingproject.entity.ChatMessage;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByRoomIdOrderByTimestampAsc(String roomId);

    @Modifying
    @Query("DELETE FROM ChatMessage c WHERE c.senderId = :senderId")
    void deleteBySenderId(@Param("senderId") Long senderId);

    @Query("SELECT COUNT(c) FROM ChatMessage c WHERE c.senderId = :senderId")
    long countBySenderId(@Param("senderId") Long senderId);
}