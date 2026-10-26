package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PostComment
import com.example.model.SocialPost
import com.example.ui.SovereignViewModel
import com.example.ui.components.SovereignAvatar
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FeedScreen(
    viewModel: SovereignViewModel,
    modifier: Modifier = Modifier
) {
    val posts by viewModel.feedPosts.collectAsState()
    var selectedTag by remember { mutableStateOf<String?>(null) }

    val allTags = remember(posts) {
        posts.flatMap { it.hashtags }.distinct()
    }

    val filteredPosts = remember(posts, selectedTag) {
        if (selectedTag == null) posts else posts.filter { it.hashtags.contains(selectedTag) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = AmberWarning.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    "الخلاصة محلية تجريبية؛ الحسابات والمنشورات المعروضة بيانات نموذجية وليست أخبارًا أو نتائج فعلية عن التطبيق.",
                    modifier = Modifier.padding(10.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface
                )
            }
            // Hashtag bar
            if (allTags.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedTag == null,
                            onClick = { selectedTag = null },
                            label = { Text("الموجز العام") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = EmeraldPrimary
                            )
                        )
                    }
                    items(allTags) { tag ->
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = { selectedTag = if (selectedTag == tag) null else tag },
                            label = { Text("#$tag") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = EmeraldPrimary
                            )
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(filteredPosts, key = { it.id }) { post ->
                    SocialPostCard(
                        post = post,
                        onLike = { viewModel.toggleLikePost(post.id) },
                        onRepost = { viewModel.toggleRepostPost(post.id) },
                        onAddComment = { text -> viewModel.addComment(post.id, text) }
                    )
                    HorizontalDivider(color = SovereignBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                }
            }
        }

        // New Post FAB
        FloatingActionButton(
            onClick = { viewModel.setShowNewPost(true) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("new_post_fab"),
            containerColor = EmeraldPrimary,
            contentColor = SovereignVoid,
            shape = CircleShape
        ) {
            Icon(Icons.Default.Edit, contentDescription = "New Post")
        }
    }
}

@Composable
fun SocialPostCard(
    post: SocialPost,
    onLike: () -> Unit,
    onRepost: () -> Unit,
    onAddComment: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    var showComments by remember { mutableStateOf(false) }
    var commentInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("social_post_${post.id}")
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            SovereignAvatar(
                name = post.authorName,
                colorHex = post.authorAvatarColor,
                sizeDp = 44,
                showShield = false
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = post.authorName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = post.authorHandle,
                            fontSize = 12.sp,
                            color = SlateMuted
                        )
                    }

                    Text(
                        text = timeFormatter.format(Date(post.timestamp)),
                        fontSize = 11.sp,
                        color = SlateMuted
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = post.content,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Hashtags
                if (post.hashtags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        post.hashtags.forEach { tag ->
                            Text(
                                text = "#$tag",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = EmeraldPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Interactions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Comments
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showComments = !showComments }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = "Comments",
                            tint = if (showComments) CyanSignal else SlateMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${post.commentsCount}",
                            fontSize = 12.sp,
                            color = if (showComments) CyanSignal else SlateMuted
                        )
                    }

                    // Repost
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .testTag("repost_btn_${post.id}")
                            .clickable(onClick = onRepost)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Repost",
                            tint = if (post.isReposted) EmeraldPrimary else SlateMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${post.repostsCount}",
                            fontSize = 12.sp,
                            color = if (post.isReposted) EmeraldPrimary else SlateMuted,
                            fontWeight = if (post.isReposted) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    // Like
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .testTag("like_btn_${post.id}")
                            .clickable(onClick = onLike)
                    ) {
                        Icon(
                            imageVector = if (post.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (post.isLiked) RoseDestruct else SlateMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${post.likesCount}",
                            fontSize = 12.sp,
                            color = if (post.isLiked) RoseDestruct else SlateMuted,
                            fontWeight = if (post.isLiked) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    // Share
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share",
                        tint = SlateMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Expanded Comments section
                AnimatedVisibility(visible = showComments) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        HorizontalDivider(color = SovereignBorder, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(8.dp))

                        post.replies.forEach { comment ->
                            CommentRow(comment = comment)
                        }

                        // Add comment input
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = commentInput,
                                onValueChange = { commentInput = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("comment_input_${post.id}"),
                                placeholder = { Text("أضف تعليقاً سيادياً...", fontSize = 12.sp) },
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldPrimary,
                                    unfocusedBorderColor = SovereignBorder,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = {
                                    if (commentInput.isNotBlank()) {
                                        onAddComment(commentInput)
                                        commentInput = ""
                                    }
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldPrimary)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send Comment",
                                    tint = SovereignVoid,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CommentRow(comment: PostComment) {
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        SovereignAvatar(
            name = comment.authorName,
            colorHex = 0xFF64748B,
            sizeDp = 28
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${comment.authorName} (${comment.authorHandle})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = timeFormatter.format(Date(comment.timestamp)),
                    fontSize = 10.sp,
                    color = SlateMuted
                )
            }
            Text(
                text = comment.text,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
