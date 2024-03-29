package com.ssafychat.domain.member.service;

import com.ssafychat.domain.member.dto.MemberDto;
import com.ssafychat.domain.member.dto.MyPageDto;
import com.ssafychat.domain.member.dto.ProfileDto;
import com.ssafychat.domain.member.dto.TokenInfoDto;
import com.ssafychat.domain.member.model.Member;
import com.ssafychat.domain.member.repository.MemberRepository;
import com.ssafychat.domain.mentoring.dto.MyPageCompleteDto;
import com.ssafychat.domain.mentoring.dto.MyPageMatchDto;
import com.ssafychat.domain.mentoring.model.CompleteMentoring;
import com.ssafychat.domain.mentoring.model.Mentoring;
import com.ssafychat.domain.mentoring.repository.CompleteMentoringRepository;
import com.ssafychat.domain.mentoring.repository.MentoringRepository;
import com.ssafychat.global.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Service
@Slf4j
public class MemberServiceImpl implements MemberService {
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private BCryptPasswordEncoder bcryptPasswordEncoder;

    @Autowired
    private MentoringRepository mentoringRepository;

    @Autowired
    private CompleteMentoringRepository completeMentoringRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private final AuthenticationManager authenticationManager;
    private final RedisTemplate<String, Object> redisTemplate;


    @Override
    public boolean registUser(MemberDto memberInfo) {
        Member checkMember = memberRepository.findByEmail(memberInfo.getEmail());

        if(checkMember == null){
            Member registUser = Member.builder().
                    job(memberInfo.getJob()).
                    belong(memberInfo.getBelong()).
                    name(memberInfo.getName()).
                    email(memberInfo.getEmail()).
                    password(bcryptPasswordEncoder.encode(memberInfo.getPassword())).
                    studentNumber(memberInfo.getStudent_number()).
                    social("싸피").
                    build();
            List<String> roleArray = registUser.getRoles();
            if (registUser.getJob().equals("")) { // 직무 없으면 멘티
                roleArray.add("role_mentee");
                registUser.setRole("role_mentee");
            } else {
                roleArray.add("role_mentor");
                registUser.setRole("role_mentor");
            }
            registUser.setRoles(roleArray);
            memberRepository.save(registUser);
            return true;
        }
        return false;
    }

    @Override
    public Map<String,String> loginUser(String email, String password) {

        Map<String,String> info = new HashMap<>();

        Member loginMember = memberRepository.findByEmail(email);

        if (loginMember == null) {
            log.error("해당하는 유저가 존재하지 않습니다.");
            info.put("message", "해당하는 유저가 존재하지 않습니다.");
            return info;
        }

        try {
            // email, password로 Authentication 객체 생성
            // authentication은 인증 여부를 확인하는 authenticated 값이 false
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(email, password);
            // 검증 (비밀번호 체크)
            // authenticate 매서드가 실행될 때 MemberDetailsService의 loadUserByUsername 실행
            Authentication authentication = authenticationManager.authenticate(authenticationToken);

            // 중복로그인 처리
            // Redis에서 userId로 저장된 Refresh Token 값이 있는지 확인 (로그인 기록이 있는지)
            String refreshToken = (String)redisTemplate.opsForValue().get("RT:" + authentication.getName());
            if(!ObjectUtils.isEmpty(refreshToken)) {
                // 기존 로그인 회원의 refresh token 삭제
                redisTemplate.delete("RT:" + authentication.getName());
            }

            // JWT 토큰 생성
            TokenInfoDto tokenInfo = jwtTokenProvider.generateToken(authentication);
            // RefreshToken Redis 저장 (expirationTime 설정을 통해 자동 삭제 처리)
            redisTemplate.opsForValue()
                    .set("RT:" + authentication.getName(), tokenInfo.getRefreshToken(), tokenInfo.getRefreshTokenExpirationTime(), TimeUnit.MILLISECONDS);

            info.put("accessToken", tokenInfo.getAccessToken());
            info.put("refreshToken", tokenInfo.getRefreshToken());
            info.put("name", loginMember.getName());
            info.put("role", loginMember.getRole());
        } catch (AuthenticationException e) {
            info.put("message", "비밀번호를 확인해주십시오.");
            return info;
        }
        return info;
    }

    @Override
    public Map<String, String> reissue(TokenInfoDto reissue) {

        Map<String, String> response = new HashMap<>();

        // Refresh Token 검증
        if (!jwtTokenProvider.validateToken(reissue.getRefreshToken())) {
            log.error("Refresh Token 정보가 유효하지 않습니다.");
            response.put("message", "Refresh Token 정보가 유효하지 않습니다.");
            return response;
        }

        // Access Token에서 userId 가져오기
        Authentication authentication = jwtTokenProvider.getAuthentication(reissue.getAccessToken());

        // Redis에서 userId로 저장된 Refresh Token 값 가져오기
        String refreshToken = (String)redisTemplate.opsForValue().get("RT:" + authentication.getName());
        // 로그아웃되어 Redis에 Refresh Token이 없는 경우
        if(ObjectUtils.isEmpty(refreshToken)) {
            log.error("Refresh Token 정보가 유효하지 않습니다.");
            response.put("message", "Refresh Token 정보가 유효하지 않습니다.");
            return response;
        }
        // Refresh Token이 일치하지 않는 경우
        if(!refreshToken.equals(reissue.getRefreshToken())) {
            log.error("Refresh Token 정보가 일치하지 않습니다.");
            response.put("message", "Refresh Token 정보가 일치하지 않습니다.");
            return response;
        }

        // 새로운 토큰 생성
        TokenInfoDto tokenInfo = jwtTokenProvider.generateToken(authentication);

        response.put("accessToken", tokenInfo.getAccessToken());
        response.put("message", "accessToken 재발급 성공");

        return response;
    }

    @Override
    public Map<String, String> logout(HttpServletRequest request) {

        Map<String, String> response = new HashMap<>();

        String bearerToken = request.getHeader("Authorization");
        if (!StringUtils.hasText(bearerToken)) {
            log.error("토큰 없음");
            response.put("message", "토큰이 없습니다.");
            return response;
        }
        String accessToken = bearerToken.substring(7);

        // Access Token 검증
        if (!jwtTokenProvider.validateToken(accessToken)) {
            log.error("잘못된 요청입니다.");
            response.put("message", "잘못된 요청입니다.");
            return response;
        }

        // Access Token에서 UserId 가져오기
        Authentication authentication = jwtTokenProvider.getAuthentication(accessToken);

        // Redis에서 해당 UserId로 저장된 Refresh Token이 있는지 확인 후 있을 경우 삭제
        if (redisTemplate.opsForValue().get("RT:" + authentication.getName()) != null) {
            // Refresh Token 삭제
            redisTemplate.delete("RT:" + authentication.getName());
        }

        // 해당 Access Token 유효시간 가지고 와서 BlackList로 저장
        Long expiration = jwtTokenProvider.getExpiration(accessToken);
        redisTemplate.opsForValue()
                .set(accessToken, "logout", expiration, TimeUnit.MILLISECONDS);

        response.put("message", "로그아웃 되었습니다.");

        return response;
    }

    @Override
    public Map<String,Object> userInfo(Member member){
        System.out.println("userInfo 호출");
        Map<String,Object> info = new HashMap<>();
        Member token_user = (Member) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        info.put("name",token_user.getName());
        info.put("email",token_user.getEmail());
        info.put("studentNumber",token_user.getStudentNumber());
        info.put("social",token_user.getSocial());
        info.put("job",token_user.getJob());
        info.put("belong",token_user.getBelong());
        info.put("totalScore",token_user.getTotalScore());
        info.put("role",token_user.getRole());

        return info;
    }

    @Override
    public MyPageDto getMypage(Member member) {
        // user 정보에서 role 확인해서 서비스 호출
        String role = member.getRole();
        ProfileDto profile = ProfileDto.builder()
                .userId(member.getUserId())
                .name(member.getName())
                .belong(member.getBelong())
                .job(member.getJob())
                .email(member.getEmail())
                .totalScore(member.getTotalScore())
                .studentNumber(member.getStudentNumber())
                .build();

        List<Mentoring> matchMentorings = new ArrayList<>();
        List<CompleteMentoring> completeMentorings = new ArrayList<>();
        List<MyPageMatchDto> matches = new ArrayList<>();
        List<MyPageCompleteDto> completes = new ArrayList<>();

        // 멘티라면 mentee_uid로 조회 : mentoring 테이블, completeMentoring 테이블
        if (role.equals("role_mentee")){
            matchMentorings = mentoringRepository.findByMentee(member);
            for (Mentoring match : matchMentorings) {
                Member mentor = memberRepository.findByUserId(match.getMentor().getUserId());
                matches.add(MyPageMatchDto.builder()
                        .mentoringId(match.getMentoringId())
                        .mentee(profile)
                        .mentor(ProfileDto.builder()
                                .userId(mentor.getUserId())
                                .name(mentor.getName())
                                .belong(mentor.getBelong())
                                .job(mentor.getJob())
                                .email(mentor.getEmail())
                                .totalScore(mentor.getTotalScore())
                                .studentNumber(mentor.getStudentNumber())
                                .build())
                        .time(match.getTime())
                        .build());
            }
            completeMentorings = completeMentoringRepository.findByMentee(member);
            for (CompleteMentoring complete : completeMentorings) {
                Member mentor = memberRepository.findByUserId(complete.getMentor().getUserId());
                completes.add(MyPageCompleteDto.builder()
                        .completeMentoringId(complete.getCompleteMentoringId())
                        .mentee(profile)
                        .mentor(ProfileDto.builder()
                                .userId(mentor.getUserId())
                                .name(mentor.getName())
                                .belong(mentor.getBelong())
                                .job(mentor.getJob())
                                .email(mentor.getEmail())
                                .totalScore(mentor.getTotalScore())
                                .studentNumber(mentor.getStudentNumber())
                                .build())
                        .time(complete.getTime())
                        .build());
            }
        // 멘토라면 mentor_uid로 조회 : mentoring 테이블, completeMentoring 테이블
        } else if (role.equals("role_mentor")) {
            matchMentorings = mentoringRepository.findByMentor(member);
            for (Mentoring match : matchMentorings) {
                Member mentee = memberRepository.findByUserId(match.getMentee().getUserId());
                matches.add(MyPageMatchDto.builder()
                        .mentoringId(match.getMentoringId())
                        .mentor(profile)
                        .mentee(ProfileDto.builder()
                                .userId(mentee.getUserId())
                                .name(mentee.getName())
                                .belong(mentee.getBelong())
                                .job(mentee.getJob())
                                .email(mentee.getEmail())
                                .totalScore(mentee.getTotalScore())
                                .studentNumber(mentee.getStudentNumber())
                                .build())
                        .time(match.getTime())
                        .build());
            }
            completeMentorings = completeMentoringRepository.findByMentor(member);
            for (CompleteMentoring complete : completeMentorings) {
                Member mentee = memberRepository.findByUserId(complete.getMentee().getUserId());
                completes.add(MyPageCompleteDto.builder()
                        .completeMentoringId(complete.getMentee().getUserId())
                        .mentor(profile)
                        .mentee(ProfileDto.builder()
                                .userId(mentee.getUserId())
                                .name(mentee.getName())
                                .belong(mentee.getBelong())
                                .job(mentee.getJob())
                                .email(mentee.getEmail())
                                .totalScore(mentee.getTotalScore())
                                .studentNumber(mentee.getStudentNumber())
                                .build())
                        .time(complete.getTime())
                        .build());
            }
        }

        // 멤버 정보까지 담아서 반환
        return MyPageDto.builder()
                .member(profile)
                .matchMentorings(matches)
                .completeMentorings(completes)
                .build();
    }

}
